/**
 * Copyright 2012-2014 Continuuity, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.continuuity.http;

import com.google.common.base.Preconditions;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * HttpDispatcher that routes HTTP requests to appropriate http handler methods. The mapping between uri paths to
 * methods that handle particular path is managed using jax-rs annotations. {@code HttpMethodHandler} routes to method
 * whose @Path annotation matches the http request uri.
 */
public class RequestRouter extends SimpleChannelUpstreamHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HttpDispatcher.class);
  private final HttpResourceHandler httpMethodHandler;
  private static final int CHUNK_MEMORY_LIMIT = 1024 * 1024 * 1024;

  public RequestRouter(HttpResourceHandler methodHandler) {
    this.httpMethodHandler = methodHandler;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    Object message = e.getMessage();
    if (!(message instanceof HttpRequest)) {
      super.messageReceived(ctx, e);
      return;
    }
    if (handleRequest((HttpRequest) message, ctx.getChannel())) {
      ctx.sendUpstream(e);
    }

  }

  private boolean handleRequest(HttpRequest httpRequest, Channel channel) {
    Preconditions.checkNotNull(httpMethodHandler, "Http Handler factory cannot be null");

    // If the request is of type BodyConsumer we will stream , otherwise we will use chunkAggregator

    Method handlerMethod = httpMethodHandler.getDestinationMethod(httpRequest, new BasicHttpResponder(channel, HttpHeaders.isKeepAlive(httpRequest)));
    if (handlerMethod == null)
        return false;
    NettyHttpService.invokeMethod.set(channel, handlerMethod);
    if (handlerMethod.getReturnType().equals(BodyConsumer.class))
    {
      if (channel.getPipeline().get("aggregator") !=null) {
        channel.getPipeline().remove("aggregator");
      }
    }
    else{
      if (channel.getPipeline().get("aggregator") == null) {
        channel.getPipeline().addAfter("router", "aggregator", new HttpChunkAggregator(CHUNK_MEMORY_LIMIT));
      }
    }
    return true;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    LOG.error("Exception caught in channel processing.", e.getCause());
    ctx.getChannel().close();
  }
}
