/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.annotations;

import hu.bme.mit.ftsrg.hypernate.mappers.AttributeMapper;
import hu.bme.mit.ftsrg.hypernate.mappers.ObjectToString;

public @interface AttributeInfo {
  String name();

  Class<? extends AttributeMapper> mapper() default ObjectToString.class;
}
