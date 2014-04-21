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

import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * HttpMethodInfo is a helper class having state information about the http handler method to be invoked, the handler
 * and arguments required for invocation by the Dispatcher. RequestRouter populates this class and stores in its context
 * as attachment.
 */
public class HttpMethodInfo {

  private final Method method;
  private final HttpHandler handler;
  private final Object[] args;
  private final HttpResponder responder;

  public HttpMethodInfo(Method method, HttpHandler handler, Object[] args, HttpResponder responder) {
    this.method = method;
    this.handler = handler;
    this.args = args;
    this.responder = responder;
  }

  /**
   * Returns BodyConsumer interface of the http handler method.
   * @return
   */
  public BodyConsumer invokeStreamingMethod() {
    BodyConsumer result = null;
    try {
      result = (BodyConsumer) method.invoke(handler, args);
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, String.format("Error in accessing path"));
    } catch (InvocationTargetException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, String.format("Error in executing path"));
    }
    return result;
  }

  /**
   * Calls the httpHandler method
   */
  public void invoke() {
    try {
      method.invoke(handler, args);
    } catch (IllegalAccessException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, String.format("Error in accessing path"));
    } catch (InvocationTargetException e) {
      responder.sendError(HttpResponseStatus.INTERNAL_SERVER_ERROR, String.format("Error in executing path"));
    }
  }

  /**
   * Sends the error to responder.
   */
  public void sendError(HttpResponseStatus status, String message) {
    responder.sendError(status, message);
  }

  /**
   * Returns true if the handler method's return type is BodyConsumer
   * @return
   */
  public boolean isStreaming() {
    if (BodyConsumer.class.isAssignableFrom(method.getReturnType())) {
      return true;
    }
    return false;
  }

  /**
   * Returns the handler method  passed through reflection from HttpResourceModel,
   * this can be used to invoke the method in Dispatcher.
   */
  public Method getMethod() {
    return method;
  }

}
