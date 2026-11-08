package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import hu.bme.mit.ftsrg.hypernate.HypernateException;
import lombok.experimental.StandardException;

@StandardException
public class MissingPreviousSchemaVersionException extends HypernateException {
    public MissingPreviousSchemaVersionException(String type, int version){
        super(String.format("Entity %s doesn't have version %d ont he ledger.", type, version));
    }
}
