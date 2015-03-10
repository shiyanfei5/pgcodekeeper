package ru.taximaxim.codekeeper.apgdiff.model.graph;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jgrapht.event.TraversalListenerAdapter;
import org.jgrapht.event.VertexTraversalEvent;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;

import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.TreeElement.DbObjType;
import cz.startnet.utils.pgdiff.PgCodekeeperException;
import cz.startnet.utils.pgdiff.PgDiffScript;
import cz.startnet.utils.pgdiff.schema.PgColumn;
import cz.startnet.utils.pgdiff.schema.PgConstraint;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgIndex;
import cz.startnet.utils.pgdiff.schema.PgSchema;
import cz.startnet.utils.pgdiff.schema.PgSequence;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import cz.startnet.utils.pgdiff.schema.PgStatementWithSearchPath;
import cz.startnet.utils.pgdiff.schema.PgTable;
import cz.startnet.utils.pgdiff.schema.PgTrigger;
import cz.startnet.utils.pgdiff.schema.StatementActions;

/**
 * Служит для отслеживания зависимостей, при этом старое состояние храниться в
 * oldDb, a новое состояние в newDb, и требуется список объектов для удаления
 * или создания при приведении состояния из старого в новое.
 */
public class DepcyResolver {

    private static final String DEPCY_PATTERN = "-- DEPCY: This {0} depends on the {1}: {2}";
    private PgDatabase oldDb;
    private PgDatabase newDb;
    private DepcyGraph oldDepcyGraph;
    private DepcyGraph newDepcyGraph;
    private Set<ActionContainer> actions = new LinkedHashSet<>();
    private List<PgSequence> sequencesOwnedBy = new ArrayList<>();

    public DepcyResolver(PgDatabase oldDatabase, PgDatabase newDatabase)
            throws PgCodekeeperException {
        this.oldDb = oldDatabase;
        this.newDb = newDatabase;
        this.oldDepcyGraph = new DepcyGraph(oldDatabase);
        this.newDepcyGraph = new DepcyGraph(newDatabase);
    }

    /**
     * При удалении объекта из старой базы добавляет для удаления все объекты из
     * старой базы. <br>
     * Объекта не существует в новой базе, но существует в старой, мы его
     * удаляем, и удаляем из старой базы все объекты, которым этот объект
     * требуется, т.к. они будут ошибочны, при отсутсвии этого объекта.
     * 
     * @param toDrop
     *            объект для удаления из старой базы
     */
    public void addDropStatements(PgStatement toDrop) {
        toDrop = getObjectFromDB(toDrop, oldDb);
        if (oldDepcyGraph.getReversedGraph().containsVertex(toDrop)) {
            DepthFirstIterator<PgStatement, DefaultEdge> dfi = new DepthFirstIterator<>(
                    oldDepcyGraph.getReversedGraph(), toDrop);
            customIteration(dfi, new DropTraversalAdapter(toDrop));
        }
    }

    /**
     * При создании объекта в новой базе добавляет для создания все объекты из
     * новой базы. <br>
     * Объект существует в новой базе, но не существует в старой, мы его
     * создаем, а также добавляем для создания все объекты, которые трубуются
     * для правильной работы создаваемого объекта.
     * 
     * @param toCreate
     */
    public void addCreateStatements(PgStatement toCreate) {
        toCreate = getObjectFromDB(toCreate, newDb);
        if (newDepcyGraph.getGraph().containsVertex(toCreate)) {
            DepthFirstIterator<PgStatement, DefaultEdge> dfi = new DepthFirstIterator<>(
                    newDepcyGraph.getGraph(), toCreate);
            customIteration(dfi, new CreateTraversalAdapter(toCreate));
        }
    }

    /**
     * При изменении объекта в старой базе, нужно попробовать изменить все
     * объекты из старой базы. <br>
     * Объект существует в обеих базах, но различается. Нужно попробовать
     * привести его к новому виду, при этом все объекты которым он нужен, также
     * нужно привести к новому виду. если это не возможно их нужно удалить.
     * (Затем создать из нового состояния)
     * 
     * @param toAlter
     */
    public void addAlterStatements(PgStatement toAlter) {
        toAlter = getObjectFromDB(toAlter, oldDb);
        if (oldDepcyGraph.getGraph().containsVertex(toAlter)) {
            DepthFirstIterator<PgStatement, DefaultEdge> dfi = new DepthFirstIterator<>(
                    oldDepcyGraph.getReversedGraph(), toAlter);
            customIteration(dfi, new AlterTraversalAdapter(toAlter));
        }
    }

    /**
     * Проходит по итератору и заполняет список объектами из итератора
     * 
     * @param dfi
     *            итератор для прохода
     * @param action
     *            список объектов из итератора
     */
    private void customIteration(
            DepthFirstIterator<PgStatement, DefaultEdge> dfi,
            CustomTraversalListenerAdapter adapter) {
        dfi.addTraversalListener(adapter);
        while (dfi.hasNext()) {
            dfi.next();
        }
    }

    public void addCustomDepciesToOld(
            List<Entry<PgStatement, PgStatement>> depcies) {
        oldDepcyGraph.addCustomDepcies(depcies);
    }

    public void addCustomDepciesToNew(
            List<Entry<PgStatement, PgStatement>> depcies) {
        newDepcyGraph.addCustomDepcies(depcies);
    }

    public void fillScript(PgDiffScript script) {
        String currentSearchPath = MessageFormat.format(
                ApgdiffConsts.SEARCH_PATH_PATTERN, "public");
        for (ActionContainer action : actions) {
            PgStatement oldObj = action.getOldObj();
            String depcy = null;
            PgStatement objStarter = action.getStarter();
            if (objStarter != null && objStarter != oldObj) {
                String objName = "";
                if (objStarter.getStatementType() == DbObjType.COLUMN) {
                    objName = ((PgColumn) objStarter).getParent().getName()
                            + ".";
                }
                objName += objStarter.getName();
                depcy = MessageFormat.format(DEPCY_PATTERN,
                        oldObj.getStatementType(),
                        objStarter.getStatementType(), objName);
            }
            switch (action.getAction()) {
            case CREATE:
                currentSearchPath = setSearchPath(currentSearchPath, oldObj,
                        script);
                if (depcy != null) {
                    script.addStatement(depcy);
                }
                script.addCreate(oldObj, null, oldObj.getCreationSQL(), true);
                break;
            case DROP:
                currentSearchPath = setSearchPath(currentSearchPath, oldObj,
                        script);
                if (depcy != null) {
                    script.addStatement(depcy);
                }
                script.addDrop(oldObj, null, oldObj.getDropSQL());
                break;
            case ALTER:
                StringBuilder sb = new StringBuilder();
                oldObj.appendAlterSQL(action.getNewObj(), sb,
                        new AtomicBoolean());
                if (sb.length() > 0) {
                    currentSearchPath = setSearchPath(currentSearchPath,
                            oldObj, script);
                    if (depcy != null) {
                        script.addStatement(depcy);
                    }
                    script.addStatement(sb.toString());
                }
                break;
            default:
                throw new IllegalStateException("Not implemented action");
            }
        }

        for (PgSequence sequence : sequencesOwnedBy) {
            currentSearchPath = setSearchPath(currentSearchPath, sequence,
                    script);
            script.addStatement(sequence.getOwnedBySQL());
        }
    }

    private boolean inDropsList(PgStatement statement) {
        for (ActionContainer action : actions) {
            if (action.getAction() != StatementActions.DROP) {
                continue;
            }
            PgStatement drop = action.getOldObj();
            if (drop.getStatementType() == statement.getStatementType()
                    && drop.getQualifiedName().equals(
                            statement.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    public void recreateDrops() {
        List<PgStatement> toRecreate = new ArrayList<>();
        for (ActionContainer action : actions) {
            if (action.getAction() != StatementActions.DROP) {
                continue;
            }
            toRecreate.add(action.getOldObj());
        }
        for (PgStatement drop : toRecreate) {
            if (getObjectFromDB(drop, newDb) != null) {
                addCreateStatements(drop);
            }
        }
    }

    private String setSearchPath(String currentSearchPath, PgStatement st,
            PgDiffScript script) {
        if (st instanceof PgStatementWithSearchPath) {
            String searchPath = ((PgStatementWithSearchPath) st)
                    .getSearchPath();
            if (!currentSearchPath.equals(searchPath)) {
                currentSearchPath = searchPath;
                script.addStatement(((PgStatementWithSearchPath) st)
                        .getSearchPath());
            }
        }
        return currentSearchPath;
    }

    /**
     * ДОбавляет в список выражений для скрипта Выражение без зависимостей
     * 
     * @param action
     *            Какое действие нужно вызвать {@link StatementActions}
     * @param oldObj
     *            Объект из старого состояния
     * @param starter
     *            TODO
     */
    public void addToListWithoutDepcies(StatementActions action,
            PgStatement oldObj, PgStatement starter) {
        switch (action) {
        case CREATE:
            if (oldObj.getStatementType() == DbObjType.SEQUENCE) {
                if (((PgSequence) oldObj).getOwnedBy() != null) {
                    sequencesOwnedBy.add((PgSequence) oldObj);
                }
            }
        case DROP:
            actions.add(new ActionContainer(oldObj, oldObj, action, starter));
            break;
        case ALTER:
            actions.add(new ActionContainer(oldObj, getObjectFromDB(oldObj,
                    newDb), action, starter));
            break;
        default:
            throw new IllegalStateException("Not implemented action");
        }
    }

    private class CreateTraversalAdapter extends CustomTraversalListenerAdapter {

        CreateTraversalAdapter(PgStatement starter) {
            super(starter, StatementActions.CREATE);
        }

        @Override
        protected boolean notAllowedToAdd(PgStatement statement) {
            if (super.notAllowedToAdd(statement)) {
                return true;
            }
            if (inDropsList(statement)) {
                return false;
            }
            if (getObjectFromDB(statement, oldDb) != null) {
                return true;
            }
            if (statement.getStatementType() == DbObjType.COLUMN) {
                PgStatement oldTable = getObjectFromDB(statement.getParent(),
                        oldDb);
                if (oldTable == null) {
                    return true;
                }
            }
            return false;
        }
    }

    private class DropTraversalAdapter extends CustomTraversalListenerAdapter {

        DropTraversalAdapter(PgStatement starter) {
            super(starter, StatementActions.DROP);
        }

        @Override
        protected boolean notAllowedToAdd(PgStatement statement) {
            if (super.notAllowedToAdd(statement)) {
                return true;
            }
            if (statement.getStatementType() == DbObjType.COLUMN) {
                PgStatement newTable = getObjectFromDB(statement.getParent(),
                        newDb);
                if (newTable != null) {
                    return false;
                } else {
                    return true;
                }
            }
            return false;
        }
    }

    private class AlterTraversalAdapter extends CustomTraversalListenerAdapter {

        AlterTraversalAdapter(PgStatement starter) {
            super(starter, StatementActions.ALTER);
        }

        @Override
        protected boolean notAllowedToAdd(PgStatement statement) {
            if (super.notAllowedToAdd(statement)) {
                return true;
            }
            PgStatement newSt = getObjectFromDB(statement, newDb);
            StringBuilder sb = new StringBuilder();
            AtomicBoolean isNeedDepcies = new AtomicBoolean(true);
            statement.appendAlterSQL(newSt, sb, isNeedDepcies);
            if (sb.length() > 0) {
                action = StatementActions.ALTER;
                return false;
            } else {
                action = StatementActions.DROP;
                return false;
            }
        }
    }

    private abstract class CustomTraversalListenerAdapter extends
            TraversalListenerAdapter<PgStatement, DefaultEdge> {
        private PgStatement starter;
        protected StatementActions action;

        CustomTraversalListenerAdapter(PgStatement starter,
                StatementActions action) {
            this.starter = starter;
            this.action = action;
        }

        @Override
        public void vertexFinished(VertexTraversalEvent<PgStatement> e) {
            PgStatement statement = e.getVertex();
            if (notAllowedToAdd(statement)) {
                return;
            }
            if (statement.getStatementType() != DbObjType.DATABASE) {
                addToList(statement);
            }
        }

        protected boolean notAllowedToAdd(PgStatement statement) {
            if (statement.getStatementType() == null) {
                return true;
            }
            return false;
        }

        protected void addToList(PgStatement statement) {
            addToListWithoutDepcies(action, statement, starter);
        }
    }

    private PgStatement getObjectFromDB(PgStatement statement, PgDatabase db) {
        PgSchema oldSchema = null;
        switch (statement.getStatementType()) {
        case EXTENSION:
            return db.getExtension(statement.getName());
        case SCHEMA:
            return db.getSchema(statement.getName());
        default:
            break;
        }
        if (statement instanceof PgStatementWithSearchPath) {
            oldSchema = db.getSchema(((PgStatementWithSearchPath) statement)
                    .getContainerSchema().getName());
        }
        if (oldSchema == null) {
            return null;
        }
        switch (statement.getStatementType()) {
        case VIEW:
            return oldSchema.getView(statement.getName());
        case TABLE:
            return oldSchema.getTable(statement.getName());
        case TRIGGER:
            PgTrigger trig = (PgTrigger) statement;
            PgTable table = oldSchema.getTable(trig.getTableName());
            if (table != null) {
                return table.getTrigger(trig.getName());
            }
            break;
        case INDEX:
            PgIndex ind = (PgIndex) statement;
            PgTable tableInd = oldSchema.getTable(ind.getTableName());
            if (tableInd != null) {
                return tableInd.getIndex(ind.getName());
            }
            break;
        case CONSTRAINT:
            PgConstraint constr = (PgConstraint) statement;
            PgTable tableConstr = oldSchema.getTable(constr.getTableName());
            if (tableConstr != null) {
                return tableConstr.getConstraint(constr.getName());
            }
            break;
        case COLUMN:
            PgColumn column = (PgColumn) statement;
            PgTable tableCol = oldSchema.getTable(column.getParent().getName());
            if (tableCol != null) {
                return tableCol.getColumn(column.getName());
            }
            break;
        case SEQUENCE:
            return oldSchema.getSequence(statement.getName());
        case FUNCTION:
            return oldSchema.getFunction(statement.getName());
        case CONTAINER:
        case DATABASE:
        default:
            break;
        }
        return null;
    }

    public Set<PgStatement> getOrderedDepcies(StatementActions actionType) {
        Set<PgStatement> result = new LinkedHashSet<>();
        for (ActionContainer obj : actions) {
            if (obj.getAction() == actionType) {
                result.add(obj.getOldObj());
            }
        }
        return result;
    }

    public void appendALter(PgStatement oldObj, PgStatement newObj) {
        if (newObj != null) {
            StringBuilder sb = new StringBuilder();
            AtomicBoolean isNeedDepcies = new AtomicBoolean(false);
            boolean retVal = oldObj.appendAlterSQL(newObj, sb, isNeedDepcies);
            if (sb.length() > 0 || retVal) {
                if (isNeedDepcies.get()) {
                    addAlterStatements(oldObj);
                } else {
                    addToListWithoutDepcies(StatementActions.ALTER, oldObj,
                            null);
                }
            }
        }
    }

    private class ActionContainer {
        private PgStatement oldObj;
        private PgStatement newObj;
        private StatementActions action;
        private PgStatement starter;

        public ActionContainer(PgStatement oldObj, PgStatement newObj,
                StatementActions action, PgStatement starter) {
            this.oldObj = oldObj;
            this.newObj = newObj;
            this.action = action;
            this.starter = starter;
        }

        public PgStatement getOldObj() {
            return oldObj;
        }

        public PgStatement getNewObj() {
            return newObj;
        }

        public StatementActions getAction() {
            return action;
        }

        public PgStatement getStarter() {
            return starter;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((action == null) ? 0 : action.hashCode());
            result = prime * result
                    + ((newObj == null) ? 0 : newObj.hashCode());
            result = prime * result
                    + ((oldObj == null) ? 0 : oldObj.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj instanceof ActionContainer) {
                ActionContainer cont = (ActionContainer) obj;
                boolean eq = action.equals(cont.getAction());
                if (oldObj != null) {
                    eq &= oldObj.compare(cont.getOldObj());
                }
                if (newObj != null) {
                    eq &= newObj.compare(cont.getNewObj());
                }
                return eq;
            }
            return false;
        }
    }
}
