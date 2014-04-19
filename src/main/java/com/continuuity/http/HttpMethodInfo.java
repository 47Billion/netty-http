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

import java.lang.reflect.Method;

/**
 * HttpMethodInfo is a helper class having state information about the http handler method to be invoked, the handler
 * and arguments required for invocation by the Dispatcher. RequestRouter populates this class and stores in its context
 * as attachment.
 */
public class HttpMethodInfo {

  private Method method;
  private HttpHandler handler;
  private Object[] args;
  private WrappedHttpResponder responder;

  HttpMethodInfo(Method method, HttpHandler handler, Object[] args){
    this.method = method;
    this.handler = handler;
    this.args = args;
  }

  /**
   * Returns WrappedHttpResponder.
   * @return
   */
  public WrappedHttpResponder getResponder() {
    return responder;
  }

  public void setResponder(WrappedHttpResponder responder) {
    this.responder = responder;
  }

  /**
   * Returns the handler method through reflection,this can be used to invoke the method in Dispatcher.
   */
  public Method getMethod() {
    return method;
  }

  public HttpHandler getHandler() {
    return handler;
  }

  public Object[] getArgs() {
    return args;
  }
}
