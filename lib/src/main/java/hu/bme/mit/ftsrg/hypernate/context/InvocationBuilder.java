/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.context;

import hu.bme.mit.ftsrg.hypernate.util.JSON;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hyperledger.fabric.shim.Chaincode.Response;

public class InvocationBuilder<T> {
  private final HypernateContext context;
  private final String chaincodeName;
  private final String functionName;
  private final String channel;
  private final Object[] typedArgs;
  private final byte[][] rawArgs;
  private final Class<T> returnType;
  private final boolean voidReturn;

  private InvocationBuilder(HypernateContext context, String chaincodeName, String functionName, 
                            String channel, Object[] typedArgs, byte[][] rawArgs, 
                            Class<T> returnType, boolean voidReturn) {
    this.context = context;
    this.chaincodeName = chaincodeName;
    this.functionName = functionName;
    this.channel = channel;
    this.typedArgs = typedArgs;
    this.rawArgs = rawArgs;
    this.returnType = returnType;
    this.voidReturn = voidReturn;
  }

  public InvocationBuilder(HypernateContext context, String chaincodeName, String functionName) {
    this(context, chaincodeName, functionName, null, new Object[0], null, null, false);
  }

  public InvocationBuilder<T> onChannel(String channel) {
    return new InvocationBuilder<>(context, chaincodeName, functionName, channel, typedArgs, rawArgs, returnType, voidReturn);
  }

  public InvocationBuilder<T> withArgs(Object... args) {
    return new InvocationBuilder<>(context, chaincodeName, functionName, channel, args, rawArgs, returnType, voidReturn);
  }

  public InvocationBuilder<T> withRawArgs(byte[]... args) {
    return new InvocationBuilder<>(context, chaincodeName, functionName, channel, typedArgs, args, returnType, voidReturn);
  }

  public <R> InvocationBuilder<R> returning(Class<R> returnType) {
    return new InvocationBuilder<>(context, chaincodeName, functionName, channel, typedArgs, rawArgs, returnType, false);
  }

  public InvocationBuilder<Void> returningVoid() {
    return new InvocationBuilder<>(context, chaincodeName, functionName, channel, typedArgs, rawArgs, null, true);
  }

  public T execute() {
    if (rawArgs != null && typedArgs != null && typedArgs.length > 0) {
      throw new IllegalStateException("Cannot use both withArgs() and withRawArgs() on the same invocation.");
    }
    if (!voidReturn && returnType == null) {
      throw new IllegalStateException("Call .returning(Class) or .returningVoid() before execute().");
    }

    List<byte[]> assembledArgs = new ArrayList<>();
    assembledArgs.add(functionName.getBytes(StandardCharsets.UTF_8));

    if (rawArgs != null) {
      assembledArgs.addAll(Arrays.asList(rawArgs));
    } else if (typedArgs != null) {
      for (int i = 0; i < typedArgs.length; i++) {
        try {
          assembledArgs.add(JSON.serialize(typedArgs[i]).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
          throw new RuntimeException("Failed to serialize typed argument at index " + i, e);
        }
      }
    }

    Response response;
    if (channel != null && !channel.isEmpty()) {
      response = context.getFabricStub().invokeChaincode(chaincodeName, assembledArgs, channel);
    } else {
      response = context.getFabricStub().invokeChaincode(chaincodeName, assembledArgs);
    }

    if (response.getStatus() != Response.Status.SUCCESS) {
      throw new CrossChaincodeException(chaincodeName, functionName, response.getStatus().getCode(), response.getMessage());
    }

    if (voidReturn) {
      return null;
    }

    try {
      return JSON.deserialize(response.getStringPayload(), returnType);
    } catch (Exception e) {
      throw new CrossChaincodeDeserializationException(chaincodeName, functionName, returnType, e);
    }
  }
}
