/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.sink.alertmanager;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.iotdb.db.sink.api.Configuration;

public class AlertManagerConfiguration implements Configuration {

  private final String host;

  public AlertManagerConfiguration(String host) {

    this.host = host;
  }

  public String getHost() {
    return host;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof org.apache.iotdb.db.sink.alertmanager.AlertManagerConfiguration)) {
      return false;
    }

    org.apache.iotdb.db.sink.alertmanager.AlertManagerConfiguration that =
        (org.apache.iotdb.db.sink.alertmanager.AlertManagerConfiguration) o;

    return new EqualsBuilder().appendSuper(super.equals(o)).append(host, that.host).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).appendSuper(super.hashCode()).append(host).toHashCode();
  }
}
