/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.middleware.notification;

import lombok.EqualsAndHashCode;
import lombok.Value;

/** Notification that should be sent before any transaction logic is executed. */
@Value
@EqualsAndHashCode(callSuper = true)
public class TransactionBegin extends HypernateNotification {}
