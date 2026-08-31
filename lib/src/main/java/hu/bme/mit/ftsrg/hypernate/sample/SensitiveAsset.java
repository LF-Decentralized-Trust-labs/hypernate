/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.sample;

import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.EndorsementPolicy;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;

@PrimaryKey({@AttributeInfo(name = "owner"), @AttributeInfo(name = "id")})
@EndorsementPolicy("OR('Org1MSP.peer', 'Org2MSP.peer')")
public class SensitiveAsset {
  private String id;
  private String color;
  private int size;
  private String owner;
  private int value;

  private long timestamp;
  private AssetStatus status;
  private String classLevel;
  private java.util.List<String> orgs;

  // Constructors
  public SensitiveAsset() {}

  public SensitiveAsset(String id, String color, int size, String owner, int value) {
    this.id = id;
    this.color = color;
    this.size = size;
    this.owner = owner;
    this.value = value;
  }

  public SensitiveAsset(
      String id,
      String color,
      int size,
      String owner,
      int value,
      long timestamp,
      AssetStatus status,
      String classLevel,
      java.util.List<String> orgs) {
    this.id = id;
    this.color = color;
    this.size = size;
    this.owner = owner;
    this.value = value;
    this.timestamp = timestamp;
    this.status = status;
    this.classLevel = classLevel;
    this.orgs = orgs;
  }

  // Getters and Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public int getSize() {
    return size;
  }

  public void setSize(int size) {
    this.size = size;
  }

  public String getOwner() {
    return owner;
  }

  public void setOwner(String owner) {
    this.owner = owner;
  }

  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public AssetStatus getStatus() {
    return status;
  }

  public void setStatus(AssetStatus status) {
    this.status = status;
  }

  public String getClassLevel() {
    return classLevel;
  }

  public void setClassLevel(String classLevel) {
    this.classLevel = classLevel;
  }

  public java.util.List<String> getOrgs() {
    return orgs;
  }

  public void setOrgs(java.util.List<String> orgs) {
    this.orgs = orgs;
  }
}
