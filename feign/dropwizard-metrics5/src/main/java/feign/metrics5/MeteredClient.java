/**
 * Copyright 2012-2021 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.metrics5;


import java.io.IOException;
import feign.*;
import feign.Request.Options;
import io.dropwizard.metrics5.MetricRegistry;
import io.dropwizard.metrics5.Timer.Context;

/**
 * Warp feign {@link Client} with metrics.
 */
public class MeteredClient implements Client {

  private final Client client;
  private final MetricRegistry metricRegistry;
  private final FeignMetricName metricName;
  private final MetricSuppliers metricSuppliers;

  public MeteredClient(Client client, MetricRegistry metricRegistry,
      MetricSuppliers metricSuppliers) {
    this.client = client;
    this.metricRegistry = metricRegistry;
    this.metricSuppliers = metricSuppliers;
    this.metricName = new FeignMetricName(Client.class);
  }

  @Override
  public Response execute(Request request, Options options) throws IOException {
    final RequestTemplate template = request.requestTemplate();
    try (final Context classTimer =
        metricRegistry.timer(
            metricName.metricName(template.methodMetadata(),
                template.feignTarget())
                .tagged("uri", template.methodMetadata().template().path()),
            metricSuppliers.timers()).time()) {
      Response response = client.execute(request, options);
      metricRegistry.counter(
          metricName
              .metricName(template.methodMetadata(), template.feignTarget(), "http_response_code")
              .tagged("http_status", String.valueOf(response.status()))
              .tagged("status_group", response.status() / 100 + "xx")
              .tagged("uri", template.methodMetadata().template().path()))
          .inc();
      return response;
    } catch (FeignException e) {
      metricRegistry.counter(
          metricName
              .metricName(template.methodMetadata(), template.feignTarget(), "http_response_code")
              .tagged("http_status", String.valueOf(e.status()))
              .tagged("status_group", e.status() / 100 + "xx")
              .tagged("uri", template.methodMetadata().template().path()))
          .inc();
      throw e;
    } catch (IOException | RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

}
