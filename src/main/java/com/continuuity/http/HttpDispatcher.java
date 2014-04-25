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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HttpDispatcher that invokes the appropriate http-handler method. The handler and the arguments are read
 * from the {@code RequestRouter} context.
 */

public class HttpDispatcher extends SimpleChannelUpstreamHandler {

  private static final Logger LOG = LoggerFactory.getLogger(HttpDispatcher.class);

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
    HttpMethodInfo methodInfo  = (HttpMethodInfo) ctx.getPipeline().getContext("router").getAttachment();
    try {
      Object message = e.getMessage();

      if (message instanceof HttpMessage) {
        methodInfo.invoke();
      } else if (message instanceof HttpChunk) {
        methodInfo.chunk((HttpChunk) message);
      } else {
        super.messageReceived(ctx, e);
      }
    } catch (Exception ex) {
      methodInfo.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, ex);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    LOG.error("Exception caught in channel processing.", e.getCause());
    ctx.getChannel().close();
  }
}
