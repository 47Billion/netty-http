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
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpDispatcher that routes HTTP requests to appropriate http handler methods. The mapping between uri paths to
 * methods that handle particular path is managed using jax-rs annotations. {@code HttpMethodHandler} routes to method
 * whose @Path annotation matches the http request uri.
 */
public class HttpDispatcher extends SimpleChannelUpstreamHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HttpDispatcher.class);
  private final HttpResourceHandler httpMethodHandler;

  public HttpDispatcher(HttpResourceHandler methodHandler) {
    this.httpMethodHandler = methodHandler;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    Object message = e.getMessage();
    if (!(message instanceof HttpRequest)) {
      super.messageReceived(ctx, e);
      return;
    }
    handleRequest((HttpRequest) message, ctx.getChannel());
  }

  private void handleRequest(HttpRequest httpRequest, Channel channel) {
    Preconditions.checkNotNull(httpMethodHandler, "Http Handler factory cannot be null");
    httpMethodHandler.handle(httpRequest, new BasicHttpResponder(channel, HttpHeaders.isKeepAlive(httpRequest)));
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    LOG.error("Exception caught in channel processing.", e.getCause());
    ctx.getChannel().close();
  }
}
