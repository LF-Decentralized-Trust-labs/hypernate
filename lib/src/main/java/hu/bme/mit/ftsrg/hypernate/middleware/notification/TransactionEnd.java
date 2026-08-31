/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware.notification;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.hyperledger.fabric.contract.Context;

/**
 * Notification that should be sent after all transaction logic has been executed.
 *
 * @see hu.bme.mit.ftsrg.hypernate.contract.HypernateContract#afterTransaction(Context, Object)
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class TransactionEnd extends HypernateNotification {}
