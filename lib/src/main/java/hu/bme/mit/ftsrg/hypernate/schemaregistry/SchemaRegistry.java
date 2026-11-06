/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.schemaregistry;

import com.google.gson.JsonArray;
import com.jcabi.aspects.Loggable;
import hu.bme.mit.ftsrg.hypernate.metadata.EntityMetadataInventory;

import java.util.Set;

import org.hyperledger.fabric.shim.ChaincodeStub;

@Loggable(Loggable.DEBUG)
public class SchemaRegistry {


  private static final String SCHEMA_NAMESPACE = "hypernate_schema";

  private final ChaincodeStub stub;

  private static JsonArray schemas = new JsonArray();

  static {
    Set<Class<?>> entitys = EntityMetadataInventory.getEnityList();
  }

  SchemaRegistry(final ChaincodeStub stub) {
    this.stub=stub;
  }

}
