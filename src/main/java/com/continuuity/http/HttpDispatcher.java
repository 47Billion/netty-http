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
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * HttpDispatcher that invokes the appropriate http-handler method. The handler and the arguments are passed
 * from the {@code RequestRouter} context.
 */

public class HttpDispatcher extends SimpleChannelUpstreamHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HttpDispatcher.class);
  private final HttpResourceHandler httpMethodHandler;
  BodyConsumer streamer;
  private boolean keepAlive;

  public HttpDispatcher(HttpResourceHandler methodHandler) {
    this.httpMethodHandler = methodHandler;
    this.keepAlive = true;
    this.streamer = null;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
    HttpMethodInfo methodInfo;
    try {
      Object message = e.getMessage();
      Channel channel = ctx.getChannel();

      methodInfo = (HttpMethodInfo) ctx.getPipeline().getContext("router").getAttachment();
      Method destHandler = methodInfo.getMethod();
      if (destHandler.getReturnType().equals(BodyConsumer.class)) {
        if ((message instanceof HttpMessage) || (message instanceof HttpRequest)) {
          Object msg = e.getMessage();
          HttpMessage httpMessage = (HttpMessage) msg;
          this.keepAlive = HttpHeaders.isKeepAlive(httpMessage);
          if (this.streamer == null) {
            this.streamer = (BodyConsumer) destHandler.invoke(methodInfo.getHandler(), methodInfo.getArgs());
            this.streamer.chunk(((HttpMessage) msg).getContent(), new BasicHttpResponder(channel, HttpHeaders.isKeepAlive(httpMessage)));
          }
        } else if (message instanceof HttpChunk) {
          Object msg = e.getMessage();
          HttpChunk httpChunk = (HttpChunk) msg;
          if (this.streamer != null) {
            if (httpChunk.isLast()) {
              this.streamer.finished(new BasicHttpResponder(channel, this.keepAlive));
            } else {
              this.streamer.chunk(httpChunk.getContent(), new BasicHttpResponder(channel, this.keepAlive));
            }
          }
        }
      } else {
        if (message instanceof HttpRequest) {
          destHandler.invoke(methodInfo.getHandler(), methodInfo.getArgs());
        } else {
          super.messageReceived(ctx, e);
          return;
        }
      }
    } catch (Exception Ex) {
       methodInfo = (HttpMethodInfo) ctx.getPipeline().getContext("router").getAttachment();
       methodInfo.getResponder().sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                           String.format("Error in executing path:"));
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    LOG.error("Exception caught in channel processing.", e.getCause());
    ctx.getChannel().close();
  }
}
