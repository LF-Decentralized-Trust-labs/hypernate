package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

@StandardException 
public class SchemaNotFoundException extends HypernateException{
    public SchemaNotFoundException(String type){
        super("There isn't any schema registered under the type: " + type);
    }
}
