/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware.notification;

import lombok.EqualsAndHashCode;
import lombok.Value;

/** Notification that should be sent after all transaction logic has been executed. */
@Value
@EqualsAndHashCode(callSuper = true)
public class TransactionEnd extends HypernateNotification {}
