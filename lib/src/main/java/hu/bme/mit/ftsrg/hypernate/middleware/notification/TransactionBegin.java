/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware.notification;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.hyperledger.fabric.contract.Context;

/**
 * Notification that should be sent before any transaction logic is executed.
 *
 * @see hu.bme.mit.ftsrg.hypernate.contract.HypernateContract#beforeTransaction(Context)
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class TransactionBegin extends HypernateNotification {}
