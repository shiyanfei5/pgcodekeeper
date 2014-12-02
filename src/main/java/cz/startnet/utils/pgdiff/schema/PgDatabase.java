/**
 * Copyright 2006 StartNet s.r.o.
 *
 * Distributed under MIT license
 */
package cz.startnet.utils.pgdiff.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;

/**
 * Stores database information.
 *
 * @author fordfrog
 */
public class PgDatabase extends PgStatement {
    /**
     * Current default schema.
     */
    private PgSchema defaultSchema;
    
    private final List<PgSchema> schemas = new ArrayList<PgSchema>(1);
    private final List<PgExtension> extensions = new ArrayList<PgExtension>();
    
    private final List<String> ignoredStatements = new ArrayList<String>();
    private final List<String> ignoredDataStatements = new ArrayList<String>();
    
    private String comment;

    public PgDatabase() {
        super("DB_name_placeholder", null);
        
        addSchema(new PgSchema(ApgdiffConsts.PUBLIC, null));
        defaultSchema = schemas.get(0);
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public void setDefaultSchema(final String name) {
        defaultSchema = getSchema(name);
    }

    public PgSchema getDefaultSchema() {
        return defaultSchema;
    }

    public List<String> getIgnoredStatements() {
        return Collections.unmodifiableList(ignoredStatements);
    }

    public List<String> getIgnoredDataStatements() {
        return Collections.unmodifiableList(ignoredDataStatements);
    }

    public void addIgnoredStatement(final String ignoredStatement) {
        ignoredStatements.add(ignoredStatement);
    }

    public void addIgnoredDataStatement(final String ignoredDataStatement) {
        ignoredDataStatements.add(ignoredDataStatement);
    }

    /**
     * Returns schema of given name or null if the schema has not been found. If
     * schema name is null then default schema is returned.
     *
     * @param name schema name or null which means default schema
     *
     * @return found schema or null
     */
    public PgSchema getSchema(final String name) {
        if (name == null) {
            return getDefaultSchema();
        }

        for (final PgSchema schema : schemas) {
            if (schema.getName().equals(name)) {
                return schema;
            }
        }

        return null;
    }

    /**
     * Getter for {@link #schemas}. The list cannot be modified.
     *
     * @return {@link #schemas}
     */
    public List<PgSchema> getSchemas() {
        return Collections.unmodifiableList(schemas);
    }

    public void addSchema(final PgSchema schema) {
        schemas.add(schema);
        schema.setParent(this);
        resetHash();
    }
    
    public void replaceSchema(PgSchema oldSchema, PgSchema newSchema) {
        if (!oldSchema.getName().equals(newSchema.getName())) {
            throw new IllegalStateException("Replacing schema must have the same name");
        }
        
        int old = schemas.indexOf(oldSchema);
        if (old == -1) {
            throw new IllegalStateException("Replaced schema is not in the database");
        }
        
        schemas.remove(old);
        addSchema(newSchema);
        if (defaultSchema == oldSchema) {
            setDefaultSchema(newSchema.getName());
        }
    }
    
    /**
     * Tries to replace the definition of default public schema.
     * Only replaces default public schema like one created by
     * {@link PgDatabase#PgDatabase()} constructor.
     */
    public void tryReplacePublicDef(PgSchema newPublic) {
        if (newPublic.getName().equals(ApgdiffConsts.PUBLIC)) {
            PgSchema oldPublic = getSchema(ApgdiffConsts.PUBLIC);
        
            if (oldPublic.compare(new PgDatabase().getDefaultSchema()) 
                    && !newPublic.compare(oldPublic)) {
                oldPublic.replaceDef(newPublic);
            }
        }
    }
    
    /**
     * Returns extension of given name or null if the extension has not been found.
     * 
     * @param name extension name
     * 
     * @return found extension or null
     */
    public PgExtension getExtension(final String name) {
        for(final PgExtension ext : extensions) {
            if(ext.getName().equals(name)) {
                return ext;
            }
        }
        
        return null;
    }
    
    /**
     * Getter for {@link #extensions}. The list cannot be modified.
     * 
     * @return {@link #extensions}
     */
    public List<PgExtension> getExtensions() {
        return Collections.unmodifiableList(extensions);
    }

    public void addExtension(final PgExtension extension) {
        extensions.add(extension);
        extension.setParent(this);
        resetHash();
    }
    
    @Override
    public String getCreationSQL() {
        return null;
    }
    
    @Override
    public String getDropSQL() {
        return null;
    }
    
    @Override
    public boolean compare(PgStatement obj) {
        // for now all instances of PgDatabase considered to be shallow equal
        return obj instanceof PgDatabase;
    }

    @Override
    public boolean equals(Object obj) {
        boolean eq = false;
        
        if(this == obj) {
            eq = true;
        } else if(obj instanceof PgDatabase) {
            PgDatabase db = (PgDatabase) obj;
            
            eq = // super.equals(obj) && // redundant here
                    new HashSet<>(extensions).equals(new HashSet<>(db.extensions))
                    && new HashSet<>(schemas).equals(new HashSet<>(db.schemas));
        }
        
        return eq;
    }

    @Override
    public int computeHash() {
        final int prime = 31;
        int result = 1;
        result = prime * result + new HashSet<>(extensions).hashCode();
        result = prime * result + new HashSet<>(schemas).hashCode();
        return result;
    }

    @Override
    public PgDatabase shallowCopy() {
        PgDatabase dbDst = new PgDatabase();
        dbDst.setComment(getComment());
        return dbDst;
    }
    
    @Override
    public PgDatabase deepCopy() {
        PgDatabase copy = shallowCopy();
        
        for(PgExtension ext : extensions) {
            copy.addExtension(ext.deepCopy());
        }
        for(PgSchema schema : schemas) {
            PgSchema exists = copy.getSchema(schema.getName());
            if (exists == null) {
                copy.addSchema(schema.deepCopy());
            } else {
                copy.replaceSchema(exists, schema.deepCopy());
            }
        }
        
        return copy;
    }
    
    public static List<PgStatement> listViewsTables(PgDatabase db) {
        List<PgStatement> statements = new ArrayList<>();
        
        for (PgSchema schema : db.getSchemas()) {
            statements.addAll(schema.getViews());
            statements.addAll(schema.getTables());
        }
        return statements;
    }
}
