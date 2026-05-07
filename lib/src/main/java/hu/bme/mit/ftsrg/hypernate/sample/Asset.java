/* SPDX-License-Identifier: Apache-2.0 */
package hu.bme.mit.ftsrg.hypernate.sample;

import hu.bme.mit.ftsrg.hypernate.annotations.AttributeInfo;
import hu.bme.mit.ftsrg.hypernate.annotations.PrimaryKey;

@PrimaryKey({
  @AttributeInfo(name = "owner"),
  @AttributeInfo(name = "id")
})
public class Asset {
  private String id;
  private String color;
  private int size;
  private String owner;
  private int value;

  private long timestamp;
  private AssetStatus status;

  // Constructors
  public Asset() {}

  public Asset(String id, String color, int size, String owner, int value) {
    this.id = id;
    this.color = color;
    this.size = size;
    this.owner = owner;
    this.value = value;
  }

  public Asset(String id, String color, int size, String owner, int value, long timestamp, AssetStatus status) {
    this.id = id;
    this.color = color;
    this.size = size;
    this.owner = owner;
    this.value = value;
    this.timestamp = timestamp;
    this.status = status;
  }

  // Getters and Setters
  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getColor() { return color; }
  public void setColor(String color) { this.color = color; }
  public int getSize() { return size; }
  public void setSize(int size) { this.size = size; }
  public String getOwner() { return owner; }
  public void setOwner(String owner) { this.owner = owner; }
  public int getValue() { return value; }
  public void setValue(int value) { this.value = value; }
  public long getTimestamp() { return timestamp; }
  public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
  public AssetStatus getStatus() { return status; }
  public void setStatus(AssetStatus status) { this.status = status; }
}
