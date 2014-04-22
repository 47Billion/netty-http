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

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RequestRouter that uses {@code HttpMethodHandler} to determine the http-handler method Signature of http request. It
 * uses this signature to dynamically configure the Netty Pipeline. Http Handler methods with return-type BodyConsumer
 * will be streamed , while other methods will use HttpChunkAggregator
 */

public class RequestRouter extends SimpleChannelUpstreamHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HttpDispatcher.class);
  private static final int CHUNK_MEMORY_LIMIT = 64 * 1024;

  private final HttpResourceHandler httpMethodHandler;

  public RequestRouter(HttpResourceHandler methodHandler) {
    this.httpMethodHandler = methodHandler;
  }

  /**
   * If the HttpRequest is valid and handled it will be sent upstream, if it cannot be invoked
   * the response will be written back immediately.
   * @param ctx
   * @param e
   * @throws Exception
   */
  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    Object message = e.getMessage();
    if (!(message instanceof HttpRequest)) {
      super.messageReceived(ctx, e);
      return;
    }
    HttpRequest request = (HttpRequest) message;
    if (handleRequest(request, ctx.getChannel(), ctx)) {
      ctx.sendUpstream(e);
    }
  }

  /**
   * If return type of the handler http method is BodyConsumer, remove "HttpChunkAggregator" class if it exits
   * Else add the HttpChunkAggregator to the pipeline if its not present already.
   */
  private boolean handleRequest(HttpRequest httpRequest, Channel channel, ChannelHandlerContext ctx) {

    HttpMethodInfo methodInfo = httpMethodHandler.getDestinationMethod(
      httpRequest, new BasicHttpResponder(channel, HttpHeaders.isKeepAlive(httpRequest)));

    if (methodInfo == null) {
      return false;
    }
    ctx.setAttachment(methodInfo);
    if (methodInfo.isStreaming()) {
      if (channel.getPipeline().get("aggregator") != null) {
        channel.getPipeline().remove("aggregator");
      }
    } else {
      if (channel.getPipeline().get("aggregator") == null) {
        channel.getPipeline().addAfter("router", "aggregator", new HttpChunkAggregator(CHUNK_MEMORY_LIMIT));
      }
    }
    return true;
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    LOG.error("Exception caught in channel processing.", e.getCause());
    HttpMethodInfo info  = (HttpMethodInfo) ctx.getAttachment();
    info.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getCause().getMessage());
    ctx.getChannel().close();
  }
}
