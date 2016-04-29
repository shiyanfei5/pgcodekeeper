package cz.startnet.utils.pgdiff.parsers.antlr.expr;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import cz.startnet.utils.pgdiff.parsers.antlr.QNameParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alias_clauseContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.From_itemContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.From_primaryContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Function_callContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.IdentifierContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Orderby_clauseContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Schema_qualified_nameContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Select_primaryContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Select_stmtContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Select_sublistContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Table_subqueryContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.VexContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.With_clauseContext;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.With_queryContext;
import cz.startnet.utils.pgdiff.parsers.antlr.rulectx.SelectOps;
import cz.startnet.utils.pgdiff.parsers.antlr.rulectx.SelectStmt;
import cz.startnet.utils.pgdiff.parsers.antlr.rulectx.Vex;
import cz.startnet.utils.pgdiff.schema.GenericColumn;
import ru.taximaxim.codekeeper.apgdiff.Log;
import ru.taximaxim.codekeeper.apgdiff.model.difftree.DbObjType;

public class Select extends AbstractExpr {

    /**
     * The local namespace of this Select.<br>
     * String-Reference pairs keep track of external table aliases and names.<br>
     * String-null pairs keep track of internal query names that have only the Alias.
     */
    private final Map<String, GenericColumn> namespace = new HashMap<>();
    /**
     * Unaliased namespace keeps track of tables that have no Alias.<br>
     * It has to be separate since same-named unaliased tables from different schemas
     * can be used, requiring qualification.
     */
    private final Set<GenericColumn> unaliasedNamespace = new HashSet<>();
    /**
     * Column alias' are in a separate set since they have two values as the Key.
     * This is not a Map because we don't connect column aliases with their columns.<br>
     * Columns of non-dereferenceable objects are aliases by default and need not to be added to this set.
     */
    private final Map<String, Set<String>> columnAliases = new HashMap<>();
    /**
     * CTE names that current level of FROM has access to.
     */
    private final Set<String> cte = new HashSet<>();
    /**
     * Flags for proper FROM (subquery) analysis.<br>
     * {@link #findReference(String)} assumes that when inFrom is set the FROM clause
     * of that query is analyzed and skips that namespace entirely unless lateralAllowed is also set
     * (when analyzing a lateral FROM subquery or a function call).<br>
     * This assumes that {@link #from(From_itemContext)} is the first method to fill the namespace.<br>
     * Note: caller of {@link #from(From_itemContext)} is responsible for setting {@link #inFrom} flag.
     */
    private boolean inFrom;
    private boolean lateralAllowed;

    public Select(String schema) {
        super(schema);
    }

    protected Select(AbstractExpr parent) {
        super(parent);
    }

    @Override
    protected Select findCte(String cteName) {
        return cte.contains(cteName) ? this : super.findCte(cteName);
    }

    private boolean hasCte(String cteName) {
        return findCte(cteName) != null;
    }

    @Override
    protected Entry<String, GenericColumn> findReference(String schema, String name, String column) {
        if (!inFrom || lateralAllowed) {
            boolean found;
            GenericColumn dereferenced = null;
            if (schema == null && namespace.containsKey(name)) {
                found = true;
                dereferenced = namespace.get(name);
            } else if (!unaliasedNamespace.isEmpty()) {
                // simple empty check to save some allocations
                // it will almost always be empty
                for (GenericColumn unaliased : unaliasedNamespace) {
                    if (unaliased.table.equals(name) &&
                            (schema == null || unaliased.schema.equals(schema))) {
                        if (dereferenced == null) {
                            dereferenced = unaliased;
                        } else {
                            Log.log(Log.LOG_WARNING, "Ambiguous reference: " + name);
                        }
                    }
                }
                found = dereferenced != null;
            } else {
                found = false;
            }

            if (found) {
                // column aliases imply there must be a corresponding table alias
                // so we may defer their lookup until here

                // also, if we cannot dereference an existing name it's safe to assume
                // all its columns are aliases
                // this saves a lookup and extra space in columnAliases
                if (column != null && dereferenced != null) {
                    Set<String> columns = columnAliases.get(name);
                    if (columns != null && columns.contains(column)) {
                        dereferenced = null;
                    }
                }
                return new SimpleEntry<>(name, dereferenced);
            }
        }
        return super.findReference(schema, name, column);
    }

    private boolean addReference(String alias, GenericColumn object) {
        boolean exists = namespace.containsKey(alias);
        if (exists) {
            Log.log(Log.LOG_WARNING, "Duplicate namespace entry: " + alias);
        } else {
            namespace.put(alias, object);
        }
        return !exists;
    }

    private boolean addRawTableReference(GenericColumn qualifiedTable) {
        boolean exists = !unaliasedNamespace.add(qualifiedTable);
        if (exists) {
            Log.log(Log.LOG_WARNING, "Duplicate unaliased table: "
                    + qualifiedTable.schema + ' ' + qualifiedTable.table);
        }
        return !exists;
    }

    private boolean addColumnReference(String alias, String column) {
        Set<String> columns = columnAliases.get(alias);
        if (columns == null) {
            columns = new HashSet<>();
            columnAliases.put(alias, columns);
        }
        boolean exists = !columns.add(column);
        if (exists) {
            Log.log(Log.LOG_WARNING, "Duplicate column alias: " + alias + ' '  + column);
        }
        return !exists;
    }

    public List<String> select(SelectStmt select) {
        With_clauseContext with = select.withClause();
        if (with != null) {
            boolean recursive = with.RECURSIVE() != null;
            for (With_queryContext withQuery : with.with_query()) {
                String withName = withQuery.query_name.getText();

                Select_stmtContext withSelect = withQuery.select_stmt();
                if (withSelect == null) {
                    Log.log(Log.LOG_WARNING, "Skipped analisys of modifying CTE " + withName);
                    continue;
                }

                // add CTE name to the visible CTEs list after processing the query for normal CTEs
                // and before for recursive ones
                Select withProcessor = new Select(this);
                SelectStmt withStmt = new SelectStmt(withSelect);
                boolean duplicate;
                if (recursive) {
                    duplicate = !cte.add(withName);
                    withProcessor.select(withStmt);
                } else {
                    withProcessor.select(withStmt);
                    duplicate = !cte.add(withName);
                }
                if (duplicate) {
                    Log.log(Log.LOG_WARNING, "Duplicate CTE " + withName);
                }
            }
        }

        List<String> ret = selectOps(select.selectOps());

        Orderby_clauseContext orderBy = select.orderBy();
        List<VexContext> vexs = null;
        if (select.limit() != null || select.offset() != null || select.fetch() != null) {
            vexs = select.vex();
        }

        if (orderBy != null || vexs != null) {
            ValueExpr vex = new ValueExpr(this);
            if (orderBy != null) {
                vex.orderBy(orderBy);
            }
            if(vexs != null) {
                for (VexContext vexCtx : vexs) {
                    vex.vex(new Vex(vexCtx));
                }
            }
        }

        if (select.of(0) != null) {
            for (Schema_qualified_nameContext tableLock : select.schemaQualifiedName()) {
                addObjectDepcy(tableLock.identifier(), DbObjType.TABLE);
            }
        }
        return ret;
    }

    private List<String> selectOps(SelectOps selectOps) {
        List<String> ret = Collections.emptyList();
        Select_stmtContext selectStmt = selectOps.selectStmt();
        Select_primaryContext primary;

        if (selectOps.leftParen() != null && selectOps.rightParen() != null &&
                selectStmt != null) {
            ret = select(new SelectStmt(selectStmt));
        } else if (selectOps.intersect() != null || selectOps.union() != null || selectOps.except() != null) {
            // analyze each in a separate scope
            // use column names from the first one
            ret = new Select(this).selectOps(selectOps.selectOps(0));
            new Select(this).selectOps(selectOps.selectOps(1));
        } else if ((primary = selectOps.selectPrimary()) != null) {
            if (primary.SELECT() != null) {
                // from defines the namespace so it goes before everything else
                if (primary.FROM() != null) {
                    boolean oldFrom = inFrom;
                    try {
                        inFrom = true;
                        for (From_itemContext fromItem : primary.from_item()) {
                            from(fromItem);
                        }
                    } finally {
                        inFrom = oldFrom;
                    }
                }

                ret = new ArrayList<>();
                ValueExpr vex = new ValueExpr(this);
                for (Select_sublistContext target : primary.select_list().select_sublist()) {
                    String column = vex.vex(new Vex(target.vex()));
                    ret.add(target.alias == null ? column : target.alias.getText());
                }

                if ((primary.set_qualifier() != null && primary.ON() != null)
                        || primary.WHERE() != null || primary.HAVING() != null) {

                }
            }
            // TODO
        } else {
            Log.log(Log.LOG_WARNING, "No alternative in SelectOps!");
        }
        return ret;
    }

    private void from(From_itemContext fromItem) {
        From_primaryContext primary;

        if (fromItem.LEFT_PAREN() != null && fromItem.RIGHT_PAREN() != null) {
            Alias_clauseContext joinAlias = fromItem.alias_clause();
            if (joinAlias != null) {
                // we simplify this case by analyzing joined ranges in an isolated scope
                // this way we get dependencies and don't pollute this scope with names hidden by the join alias
                // the only name this form of FROM clause exposes is the join alias

                // consequence of this method: no way to connect column references with the tables inside the join
                // that would require analyzing the table schemas and actually "performing" the join
                Select fromProcessor = new Select(this);
                fromProcessor.inFrom = true;
                fromProcessor.from(fromItem.from_item(0));
                addReference(joinAlias.alias.getText(), null);
            } else {
                from(fromItem.from_item(0));
            }
        } else if (fromItem.JOIN() != null) {
            from(fromItem.from_item(0));
            from(fromItem.from_item(1));
            VexContext joinOn = fromItem.vex();
            if (joinOn != null) {
                // TODO do we analyze this expr at this point? later?
                // column references?
            }
        } else if ((primary = fromItem.from_primary()) != null) {
            Schema_qualified_nameContext table = primary.schema_qualified_name();
            Alias_clauseContext alias = primary.alias_clause();
            Table_subqueryContext subquery;
            Function_callContext function;

            if (table != null) {
                List<IdentifierContext> tableIds = table.identifier();
                String tableName = QNameParser.getFirstName(tableIds);

                boolean isCte = tableIds.size() == 1 && hasCte(tableName);
                GenericColumn depcy = null;
                if (!isCte) {
                    depcy = addObjectDepcy(tableIds, DbObjType.TABLE);
                }

                if (alias != null) {
                    String aliasName = alias.alias.getText();
                    if (addReference(aliasName, depcy) && !isCte &&
                            !alias.column_alias.isEmpty()) {
                        for (IdentifierContext columnAlias : alias.column_alias) {
                            addColumnReference(aliasName, columnAlias.getText());
                        }
                    }
                } else if (isCte) {
                    addReference(tableName, null);
                } else {
                    addRawTableReference(depcy);
                }
            } else if ((subquery = primary.table_subquery()) != null) {
                boolean oldLateral = lateralAllowed;
                try {
                    lateralAllowed = primary.LATERAL() != null;
                    new Select(this).select(new SelectStmt(subquery.select_stmt()));
                    addReference(alias.alias.getText(), null);
                } finally {
                    lateralAllowed = oldLateral;
                }
            } else if ((function = primary.function_call()) != null) {
                boolean oldLateral = lateralAllowed;
                try {
                    lateralAllowed = true;
                    GenericColumn func = new ValueExpr(this).function(function);
                    if (func != null) {
                        String funcAlias = primary.alias == null ? func.table :
                            primary.alias.getText();
                        addReference(funcAlias, null);
                    }
                } finally {
                    lateralAllowed = oldLateral;
                }
            } else {
                Log.log(Log.LOG_WARNING, "No alternative in from_primary!");
            }
        } else {
            Log.log(Log.LOG_WARNING, "No alternative in from_item!");
        }
    }
}