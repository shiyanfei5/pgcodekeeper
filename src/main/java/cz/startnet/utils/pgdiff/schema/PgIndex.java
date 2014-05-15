/**
 * Copyright 2006 StartNet s.r.o.
 *
 * Distributed under MIT license
 */
package cz.startnet.utils.pgdiff.schema;

import java.util.Objects;

import cz.startnet.utils.pgdiff.PgDiffUtils;

/**
 * Stores table index information.
 *
 * @author fordfrog
 */
public class PgIndex extends PgStatementWithSearchPath {

    private String definition;
    private String tableName;
    private boolean unique;
    private String comment;

    public PgIndex(String name, String rawStatement, String searchPath) {
        super(name, rawStatement, searchPath);
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public String getCreationSQL() {
        final StringBuilder sbSQL = new StringBuilder(100);
        sbSQL.append("CREATE ");

        if (isUnique()) {
            sbSQL.append("UNIQUE ");
        }

        sbSQL.append("INDEX ");
        sbSQL.append(PgDiffUtils.getQuotedName(getName()));
        sbSQL.append(" ON ");
        sbSQL.append(PgDiffUtils.getQuotedName(getTableName()));
        sbSQL.append(' ');
        sbSQL.append(getDefinition());
        sbSQL.append(';');

        if (comment != null && !comment.isEmpty()) {
            sbSQL.append("\n\nCOMMENT ON INDEX ");
            sbSQL.append(PgDiffUtils.getQuotedName(name));
            sbSQL.append(" IS ");
            sbSQL.append(comment);
            sbSQL.append(';');
        }

        return sbSQL.toString();
    }

    public void setDefinition(final String definition) {
        this.definition = definition;
    }

    public String getDefinition() {
        return definition;
    }

    public String getDropSQL() {
        return "DROP INDEX " + PgDiffUtils.getQuotedName(getName()) + ";";
    }

    public void setTableName(final String tableName) {
        this.tableName = tableName;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(final boolean unique) {
        this.unique = unique;
    }

    @Override
    public boolean compare(PgStatement obj) {
        boolean equals = false;

        if (this == obj) {
            equals = true;
        } else if (obj instanceof PgIndex) {
            PgIndex index = (PgIndex) obj;
            equals = Objects.equals(definition, index.getDefinition())
                    && Objects.equals(name, index.getName())
                    && Objects.equals(tableName, index.getTableName())
                    && unique == index.isUnique();
        }

        return equals;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((definition == null) ? 0 : definition.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((tableName == null) ? 0 : tableName.hashCode());
        result = prime * result + (unique ? 1231 : 1237);
        return result;
    }
    
    @Override
    public PgIndex shallowCopy() {
        PgIndex indexDst = new PgIndex(getName(), getRawStatement(), getSearchPath());
        indexDst.setDefinition(getDefinition());
        indexDst.setTableName(getTableName());
        indexDst.setUnique(isUnique());
        indexDst.setComment(getComment());
        return indexDst;
    }
    
    @Override
    public PgIndex deepCopy() {
        return shallowCopy();
    }
}
