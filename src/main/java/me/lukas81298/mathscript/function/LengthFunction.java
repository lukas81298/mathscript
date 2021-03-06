package me.lukas81298.mathscript.function;

import me.lukas81298.mathscript.interpreter.ScriptException;
import me.lukas81298.mathscript.interpreter.BaseInterpreter;
import me.lukas81298.mathscript.struct.InternalTuple;

import java.util.List;

/**
 * @author lukas
 * @since 08.06.2018
 */
public class LengthFunction implements Function {

    @Override
    public Object execute( BaseInterpreter env, Object... arguments ) throws ScriptException {
        Object o = arguments[0];
        if ( o instanceof List ) {
            return ( (List) o ).size();
        }
        if( o instanceof InternalTuple ){
            return ((InternalTuple)o).length();
        }
        if( o instanceof String ) {
            return ((String) o).length();
        }
        throw new ScriptException( "Cannot execute length function on " + o.getClass().getSimpleName() );
    }

    @Override
    public Class<?> mapsTo() {
        return Integer.class;
    }

    @Override
    public boolean acceptsArgumentLength( int i ) {
        return i == 1;
    }
}
