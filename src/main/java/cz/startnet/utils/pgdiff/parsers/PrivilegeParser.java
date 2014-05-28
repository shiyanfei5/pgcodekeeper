package cz.startnet.utils.pgdiff.parsers;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import cz.startnet.utils.pgdiff.Resources;
import cz.startnet.utils.pgdiff.schema.PgDatabase;
import cz.startnet.utils.pgdiff.schema.PgFunction;
import cz.startnet.utils.pgdiff.schema.PgPrivilege;
import cz.startnet.utils.pgdiff.schema.PgSchema;
import cz.startnet.utils.pgdiff.schema.PgStatement;

/**
 * Privileges parser.
 * Currently adds privileges to SCHEMA, TABLE, VIEW, SEQUENCE, FUNCTION objects. 
 * 
 * @author Alexander Levsha
 */
public class PrivilegeParser {
    
    private final static String ROLE_ALL = "ALL";
    
    private final static String[] ROLES = {
        "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER",
        "USAGE", "EXECUTE", "CREATE"
        };
    
    private final static String[] ROLES_COLUMN = {
        "SELECT", "INSERT", "UPDATE", "REFERENCES"
        };
    
    private final static String[] OBJECTS = {
        "TABLE", "SEQUENCE", "DATABASE", "DOMAIN", "FOREIGN", "FUNCTION", "LANGUAGE",
        "LARGE", "SCHEMA", "TABLESPACE", "TYPE", "ALL"
        };
    
    public static void parse(PgDatabase db, String statement,
            boolean outputIgnoredStatements) {
        Parser p = new Parser(statement);
        
        boolean revoke = p.expectOptional("REVOKE");
        if (!revoke) {
            p.expect("GRANT");
        }
        
        int posDef = p.getPosition();
        String definition = p.getRest();
        PgPrivilege privilege = new PgPrivilege(revoke, definition, statement);
        p.setPosition(posDef);
        
        // consume everything to get the name of the object this privilege belongs to
        
        if (revoke) {
            p.expectOptional("GRANT", "OPTION", "FOR");
        }
        
        if (p.expectOptional(ROLE_ALL)) {
            p.expectOptional("PRIVILEGES");
            
            columns(p);
        } else {
            Set<String> roles = new HashSet<String>(Arrays.asList(ROLES));
            do {
                String role = p.expectOneOf(roles.toArray(new String[roles.size()]));
                // prohibit multiple entries of the same role
                roles.remove(role);
                
                for (String roleCol : ROLES_COLUMN) {
                    if (roleCol.equals(role)) {
                        columns(p);
                        break;
                    }
                }
            } while (p.expectOptional(","));
        }
        
        p.expect("ON");
        
        String objType = p.expectOptionalOneOf(OBJECTS);
        if (objType == null) {
            objType = "TABLE";
        }
        
        PgStatement obj = null;
        switch (objType) {
        case "TABLE":
        case "SEQUENCE":
        case "FUNCTION":
            obj = getStatementFromSchema(p, objType, db, statement);
            break;
            
        case "SCHEMA":
            obj = db.getSchema(ParserUtils.getObjectName(p.parseIdentifier()));
            break;
            
        default:
            if (outputIgnoredStatements) {
                db.addIgnoredStatement(statement);
            }
            return;
        }
        
        if (obj != null) {
            obj.addPrivilege(privilege);
        } else {
            p.throwUnsupportedCommand();
        }
    }

    private static PgStatement getStatementFromSchema(Parser p, String type,
            PgDatabase db, String statement) {
        String id = p.parseIdentifier();
        
        String schemaName = ParserUtils.getSchemaName(id, db);
        PgSchema schema = db.getSchema(schemaName);
        if (schema == null) {
            throw new RuntimeException(MessageFormat.format(
                    Resources.getString("CannotFindSchema"), schemaName,
                    statement));
        }
        
        String objName = ParserUtils.getObjectName(id);
        PgStatement obj = null;
        switch(type) {
        case "TABLE":
            // table syntax can also be used for views and sequences
            obj = schema.getTable(objName);
            if (obj == null) {
                obj = schema.getView(objName);
            }
            if (obj == null) {
                obj = schema.getSequence(objName);
            }
            break;
            
        case "SEQUENCE":
            obj = schema.getSequence(objName);
            break;
            
        case "FUNCTION":
            PgFunction tmp = new PgFunction(objName, null, null);
            CreateFunctionParser.parseArguments(p, tmp);
            
            obj = schema.getFunction(tmp.getSignature());
            break;
        
        default:
            break;
        }
        
        return obj;
    }
    
    /**
     * @return true if columns were found
     */
    private static boolean columns(Parser p) {
        if (!p.expectOptional("(")) {
            return false;
        } else {
            do {
                p.parseIdentifier();
            } while (p.expectOptional(","));
            
            p.expect(")");
            return true;
        }
    }
    
    private PrivilegeParser() {
    }
}
