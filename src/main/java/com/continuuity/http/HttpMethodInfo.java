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

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * HttpMethodInfo is a helper class having state information about the http handler method to be invoked, the handler
 * and arguments required for invocation by the Dispatcher. RequestRouter populates this class and stores in its
 * context as attachment.
 */
class HttpMethodInfo {

  private final Method method;
  private final HttpHandler handler;
  private final Object[] args;
  private final HttpResponder responder;
  private final boolean isStreaming;

  HttpMethodInfo(Method method, HttpHandler handler, Object[] args, HttpResponder responder) {
    this.method = method;
    this.handler = handler;
    this.args = args;
    this.responder = responder;
    this.isStreaming = BodyConsumer.class.isAssignableFrom(method.getReturnType());
  }

  /**
   * Returns BodyConsumer interface of the http handler method.
   * @return
   */
  BodyConsumer invokeStreamingMethod(HttpRequest request) throws Exception {
    args[0] = request; //updates the request with the passed httprequest. 0 index is set to request @ ResourceModel
    return (BodyConsumer) method.invoke(handler, args);
  }

  /**
   * Calls the httpHandler method
   */
  void invoke() throws Exception {
    method.invoke(handler, args);
  }

  /**
   * Sends the error to responder.
   */
  void sendError(HttpResponseStatus status, String message) {
    responder.sendError(status, message);
  }

  /**
   * Returns true if the handler method's return type is BodyConsumer
   * @return
   */
  boolean isStreaming() {
    return isStreaming;
  }
}
