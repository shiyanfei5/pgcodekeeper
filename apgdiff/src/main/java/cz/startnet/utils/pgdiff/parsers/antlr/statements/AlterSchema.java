package cz.startnet.utils.pgdiff.parsers.antlr.statements;

import cz.startnet.utils.pgdiff.parsers.antlr.QNameParser;
import cz.startnet.utils.pgdiff.parsers.antlr.SQLParser.Alter_schema_statementContext;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgSchema;
import cz.startnet.utils.pgdiff.schema.PgStatement;
import ru.taximaxim.codekeeper.apgdiff.ApgdiffConsts;

public class AlterSchema extends ParserAbstract {
    private final Alter_schema_statementContext ctx;
    public AlterSchema(Alter_schema_statementContext ctx, PgDatabase db) {
        super(db);
        this.ctx = ctx;
    }

    @Override
    public PgStatement getObject() {
        String name = QNameParser.getFirstName(ctx.schema_with_name().name.identifier());
        PgSchema schema = getSafe(db::getSchema,
                QNameParser.getFirstNameCtx(ctx.schema_with_name().name.identifier()));
        if (!name.equals(ApgdiffConsts.PUBLIC)) {
            fillOwnerTo(ctx.owner_to(), schema);
        }
        return null;
    }
}
