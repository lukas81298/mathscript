package me.lukas81298.mathscript.parser;

import me.lukas81298.mathscript.Types;
import me.lukas81298.mathscript.function.FunctionManager;
import me.lukas81298.mathscript.struct.InternalArrayList;
import me.lukas81298.mathscript.struct.InternalHashSet;
import me.lukas81298.mathscript.struct.Tuple;
import me.lukas81298.mathscript.util.ScriptFunction;
import me.lukas81298.mathscript.util.ScriptScanner;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author lukas
 * @since 08.06.2018
 */
public class ScriptExecutor {

    private final ScriptScanner scanner;
    private final Pattern letPattern = Pattern.compile( "(let|var|define) ([A-Za-z_][A-Za-z0-1_]{0,127}) *= *(.+)" );
    private final Pattern ifPattern = Pattern.compile( "if ?(.*)" );
    private final Pattern whilePattern = Pattern.compile( "while ?(.*)" );

    private final Map<Pattern, ScriptFunction<Matcher, Object>> patterns = new LinkedHashMap<>();

    private final FunctionManager functionManager = new FunctionManager();

    private final Map<String, Object> scopedDefinedVariables = new HashMap<>();

    public ScriptExecutor( ScriptScanner scriptScanner ) {
        this( scriptScanner, null );
    }

    public ScriptExecutor( ScriptScanner scanner, Map<String, Object> variables ) {
        this.scanner = scanner;
        if ( variables != null ) {
            this.scopedDefinedVariables.putAll( variables );
        }
        this.registerPattern( "\\[(.*)\\]", new ScriptFunction<Matcher, Object>() {
            @Override
            public Object apply( Matcher matcher ) throws ScriptException {
                String elements = matcher.group( 1 );
                final String[] split = elements.split( "," );
                List<Object> list = new InternalArrayList<>();
                for ( String s : split ) {
                    list.add( evalExpression( s ) );
                }
                return list;
            }
        } );
        this.registerPattern( "([_A-Za-z][_A-Za-z0-9]{0,127})\\((.*)\\)", new ScriptFunction<Matcher, Object>() {
            @Override
            public Object apply( Matcher functionMatcher ) throws ScriptException {
                String functionName = functionMatcher.group( 1 );
                String[] argsRaw = functionMatcher.group( 2 ).split( "," );
                Object[] argsTypes;
                if ( argsRaw[0].trim().isEmpty() ) {
                    argsTypes = new Object[0];
                } else {
                    argsTypes = new Object[argsRaw.length];
                    for ( int i = 0; i < argsRaw.length; i++ ) {
                        argsTypes[i] = evalExpression( argsRaw[i] );
                    }
                }
                return executeFunction( functionName, argsTypes );
            }
        } );
        this.registerPattern( "\\((.*)\\)", new ScriptFunction<Matcher, Object>() {
            @Override
            public Object apply( Matcher matcher ) throws ScriptException {
                String elements = matcher.group( 1 );
                final String[] split = elements.split( "," );
                Tuple tuple = new Tuple( split.length );
                for ( int i = 0; i < split.length; i++ ) {
                    tuple.set( i, evalExpression( split[i] ) );
                }
                return tuple;
            }
        } );
        this.registerPattern( "\\{(.*)\\}", new ScriptFunction<Matcher, Object>() {
            @Override
            public Object apply( Matcher matcher ) throws ScriptException {
                String elements = matcher.group( 1 );
                final String[] split = elements.split( "," );
                Set<Object> set = new InternalHashSet<>();
                for ( String s : split ) {
                    set.add( evalExpression( s ) );
                }
                return set;
            }
        } );

        this.registerPattern( "(.+)[ ]*((\\+|-|\\*|\\.|%|\\^|\\/|<=|<|>=|>|==|!=)[ ]*(.+))+", new ScriptFunction<Matcher, Object>() {
            @Override
            public Object apply( Matcher infixMatcher ) throws ScriptException {
                String left = infixMatcher.group( 1 );
                String op = infixMatcher.group( 3 );
                String right = infixMatcher.group( 4 );
                return executeFunction( op, evalExpression( left ), evalExpression( right ) );
            }
        } );

    }

    private void registerPattern( String pattern, ScriptFunction<Matcher, Object> function ) {
        this.patterns.put( Pattern.compile( pattern ), function );
    }


    public void parse() throws ScriptException {
        while ( this.scanner.hasNextLine() ) {
            String line = scanner.nextLine();
            parseLine( line );
        }
    }

    private void parseWhileBlock( String condition ) throws ScriptException {
        int index = this.scanner.index(); // store the pc of the while statement
        outer:
        while ( Objects.equals( Boolean.TRUE, evalExpression( condition ) ) ) {
            while ( this.scanner.hasNextLine() ) {
                String line = stripComment( this.scanner.nextLine().trim() );
                if ( line.toLowerCase().equals( "done" ) ) {
                    this.scanner.jump( index );
                    continue outer;
                }
                parseLine( line );
            }
            throw new ScriptException( "Unexpected end of file, missing done statement" );
        }
        while ( scanner.hasNextLine() ) {
            if( !scanner.nextLine().toLowerCase().equals( "done" ) ) {
                continue;
            }
            break;
        }
    }

    private void parseIfBlock( String condition ) throws ScriptException {
        if ( Objects.equals( Boolean.TRUE, evalExpression( condition ) ) ) { // eval expression and check if it is true
            boolean inElse = false;
            while ( this.scanner.hasNextLine() ) {
                String line = scanner.nextLine().trim();
                String lowerCase = line.toLowerCase();
                if ( lowerCase.equals( "else" ) ) {
                    inElse = true;
                    continue;
                }
                if ( lowerCase.equals( "fi" ) ) {
                    return;
                }
                if ( !inElse ) {
                    parseLine( line );
                }
            }
        } else {
            boolean inElse = false;
            while ( this.scanner.hasNextLine() ) {
                String line = scanner.nextLine().trim();
                String lowerCase = line.toLowerCase();
                if ( lowerCase.equals( "else" ) ) {
                    inElse = true;
                    continue;
                }
                if ( lowerCase.equals( "fi" ) ) {
                    return;
                }
                if ( inElse ) {
                    parseLine( line );
                }
            }
        }

    }

    private String stripComment( String line ) {
        int firstComment = line.indexOf( "//" );
        if ( firstComment >= 0 ) {
            line = line.substring( 0, firstComment ).trim();
        }
        return line;
    }

    public void parseLine( String line ) throws ScriptException {
        //    System.out.println( "Parse line " + line );
        if ( line.equals( "/*" ) ) {
            do {
                if ( !scanner.hasNextLine() ) {
                    throw new ScriptException( "Unexpected end of file, missing */" );
                }
                line = scanner.nextLine().trim();
            } while ( !line.equalsIgnoreCase( "*/" ) );
            if ( !scanner.hasNextLine() ) {
                return;
            }
            line = scanner.nextLine().trim();
        }
        line = stripComment( line );
        if ( !line.isEmpty() ) {
            Matcher matcher = letPattern.matcher( line );
            if ( matcher.find() ) {
                String varName = matcher.group( 2 );
                Object value = evalExpression( matcher.group( 3 ) );
                scopedDefinedVariables.put( varName, value );
            } else {
                Matcher ifMatched = ifPattern.matcher( line );
                if ( ifMatched.matches() ) {
                    parseIfBlock( ifMatched.group( 1 ) );
                } else {
                    Matcher whileMatched = whilePattern.matcher( line );
                    if( whileMatched.matches() ) {
                        parseWhileBlock( whileMatched.group( 1 ) );
                    } else {
                        evalExpression( line );
                    }
                }
            }
        }
    }

    public Map<String, Object> getVariables() {
        return this.scopedDefinedVariables;
    }

    private Object executeFunction( String name, Object... args ) throws ScriptException {
        return this.functionManager.executeFunction( this, name, args );
    }

    public Object evalExpression( String s ) throws ScriptException {
        s = s.trim();
        Number number = Types.checkIfNumber( s );
        if ( number != null ) {
            return number;
        }
        if ( s.equalsIgnoreCase( "true" ) ) {
            return Boolean.TRUE;
        }
        if ( s.equalsIgnoreCase( "false" ) ) {
            return Boolean.FALSE;
        }
        if ( s.equalsIgnoreCase( "null" ) || s.equalsIgnoreCase( "nil" ) || s.equalsIgnoreCase( "undefined" ) ) {
            return null;
        }

        if ( s.startsWith( "\"" ) && s.endsWith( "\"" ) ) {
            return s.length() == 2 ? "" : s.substring( 1, s.length() - 1 );
        }

        for ( Map.Entry<Pattern, ScriptFunction<Matcher, Object>> entry : this.patterns.entrySet() ) {
            Matcher matcher = entry.getKey().matcher( s );
            if ( matcher.matches() ) {
                return entry.getValue().apply( matcher );
            }
        }

        if ( scopedDefinedVariables.containsKey( s ) ) {
            return scopedDefinedVariables.get( s );
        }

        if ( s.endsWith( "!" ) ) {
            s = s.substring( 0, s.length() - 1 );
            return executeFunction( "fac", evalExpression( s ) );
        }
        if ( s.startsWith( "!" ) ) {
            s = s.substring( 1 );
            return executeFunction( "neg", evalExpression( s ) );
        }
        throw new ScriptException( "Invalid statement or undefined variable " + s );
    }

}
