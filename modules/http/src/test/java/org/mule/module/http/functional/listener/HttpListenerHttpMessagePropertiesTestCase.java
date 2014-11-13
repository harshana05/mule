/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.http.functional.listener;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import org.mule.api.MuleMessage;
import org.mule.module.http.api.HttpConstants;
import org.mule.module.http.internal.ParameterMap;
import org.mule.module.http.internal.domain.HttpProtocol;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;

import com.google.common.collect.ImmutableMap;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.apache.http.HttpVersion;
import org.apache.http.client.fluent.Request;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsNull;
import org.junit.Rule;
import org.junit.Test;

public class HttpListenerHttpMessagePropertiesTestCase extends FunctionalTestCase
{

    public static final String QUERY_PARAM_NAME = "queryParam";
    public static final String QUERY_PARAM_VALUE = "paramValue";
    public static final String QUERY_PARAM_VALUE_WITH_SPACES = "param Value";
    public static final String QUERY_PARAM_SECOND_VALUE = "paramAnotherValue";
    public static final String SECOND_QUERY_PARAM_NAME = "queryParam2";
    public static final String SECOND_QUERY_PARAM_VALUE = "paramValue2";
    public static final String CONTEXT_PATH = "/context/path";
    public static final String BASE_PATH = "/";

    private static final String FIRST_URI_PARAM_NAME = "uri-param1";
    private static final String SECOND_URI_PARAM_NAME = "uri-param2";
    private static final String THIRD_URI_PARAM_NAME = "uri-param3";
    public static final String FIRST_URI_PARAM = "uri-param-value-1";
    public static final String SECOND_URI_PARAM_VALUE = "uri-param-value-2";
    public static final String THIRD_URI_PARAM_VALUE = "uri-param-value-3";

    @Rule
    public DynamicPort listenPort = new DynamicPort("port");

    @Override
    protected String getConfigFile()
    {
        return "http-listener-message-properties-config.xml";
    }

    @Test
    public void get() throws Exception
    {
        final String url = String.format("http://localhost:%s", listenPort.getNumber());
        Request.Get(url).connectTimeout(1000).execute();
        final MuleMessage message = muleContext.getClient().request("vm://out", 1000);
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_REQUEST_URI), is(BASE_PATH));
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_REQUEST_PATH_PROPERTY), is(BASE_PATH));
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_QUERY_STRING), is(""));
        assertThat(message.getInboundProperty(HttpConstants.RequestProperties.HTTP_URI_PARAMS), notNullValue());
        assertThat(message.<Map>getInboundProperty(HttpConstants.RequestProperties.HTTP_URI_PARAMS).isEmpty(), is(true));
        final Map queryParams = message.getInboundProperty(HttpConstants.RequestProperties.HTTP_QUERY_PARAMS);
        assertThat(queryParams, IsNull.notNullValue());
        assertThat(queryParams.size(), is(0));
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_METHOD_PROPERTY), is("GET"));
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_VERSION_PROPERTY), is(HttpProtocol.HTTP_1_1.getProtocolName()));
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_REMOTE_ADDRESS), is(CoreMatchers.notNullValue()));
    }

    @Test
    public void getWithQueryParams() throws Exception
    {
        final ImmutableMap<String, Object> queryParams = ImmutableMap.<String, Object>builder()
                .put(QUERY_PARAM_NAME, QUERY_PARAM_VALUE)
                .put(SECOND_QUERY_PARAM_NAME, SECOND_QUERY_PARAM_VALUE)
                .build();
        final String uri = "/?" + buildQueryString(queryParams);
        final String url = String.format("http://localhost:%s" + uri, listenPort.getNumber());
        Request.Get(url).connectTimeout(1000).execute();
        final MuleMessage message = muleContext.getClient().request("vm://out", 1000);
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_REQUEST_URI), is(uri));
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_REQUEST_PATH_PROPERTY), is(BASE_PATH));
        Map<String, String> retrivedQueryParams = message.getInboundProperty(HttpConstants.RequestProperties.HTTP_QUERY_PARAMS);
        assertThat(retrivedQueryParams, IsNull.notNullValue());
        assertThat(retrivedQueryParams.size(), is(2));
        assertThat(retrivedQueryParams.get(QUERY_PARAM_NAME), is(QUERY_PARAM_VALUE));
        assertThat(retrivedQueryParams.get(SECOND_QUERY_PARAM_NAME), is(SECOND_QUERY_PARAM_VALUE));
    }

    @Test
    public void getWithQueryParamMultipleValues() throws Exception
    {
        final ImmutableMap<String, Object> queryParams = ImmutableMap.<String, Object>builder()
                .put(QUERY_PARAM_NAME, Arrays.asList(QUERY_PARAM_VALUE,QUERY_PARAM_SECOND_VALUE))
                .build();
        final String url = String.format("http://localhost:%s/?" + buildQueryString(queryParams), listenPort.getNumber());
        Request.Get(url).connectTimeout(1000).execute();
        final MuleMessage message = muleContext.getClient().request("vm://out", 1000);
        ParameterMap retrivedQueryParams = message.getInboundProperty(HttpConstants.RequestProperties.HTTP_QUERY_PARAMS);
        assertThat(retrivedQueryParams, IsNull.notNullValue());
        assertThat(retrivedQueryParams.size(), is(1));
        assertThat(retrivedQueryParams.get(QUERY_PARAM_NAME), is(QUERY_PARAM_VALUE));
        assertThat(retrivedQueryParams.getAsList(QUERY_PARAM_NAME).size(), is(2));
        assertThat(retrivedQueryParams.getAsList(QUERY_PARAM_NAME), Matchers.containsInAnyOrder(new String[]{QUERY_PARAM_VALUE, QUERY_PARAM_SECOND_VALUE}));
    }

    @Test
    public void postWithEncodedValues() throws Exception
    {
        final ImmutableMap<String, Object> queryParams = ImmutableMap.<String, Object>builder()
                .put(QUERY_PARAM_NAME, QUERY_PARAM_VALUE_WITH_SPACES)
                .build();
        final String url = String.format("http://localhost:%s/?" + buildQueryString(queryParams), listenPort.getNumber());
        Request.Post(url).connectTimeout(1000).execute();
        final MuleMessage message = muleContext.getClient().request("vm://out", 1000);
        ParameterMap retrivedQueryParams = message.getInboundProperty(HttpConstants.RequestProperties.HTTP_QUERY_PARAMS);
        assertThat(retrivedQueryParams, IsNull.notNullValue());
        assertThat(retrivedQueryParams.size(), is(1));
        assertThat(retrivedQueryParams.get(QUERY_PARAM_NAME), is(QUERY_PARAM_VALUE_WITH_SPACES));
    }

    @Test
    public void putWithOldProtocol() throws Exception
    {
        final ImmutableMap<String, Object> queryParams = ImmutableMap.<String, Object>builder()
                .put(QUERY_PARAM_NAME, Arrays.asList(QUERY_PARAM_VALUE,QUERY_PARAM_VALUE))
                .build();
        final String url = String.format("http://localhost:%s/?" + buildQueryString(queryParams), listenPort.getNumber());
        Request.Put(url).version(HttpVersion.HTTP_1_0).connectTimeout(1000).execute();
        final MuleMessage message = muleContext.getClient().request("vm://out", 1000);
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_METHOD_PROPERTY), is("PUT"));
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_VERSION_PROPERTY), is(HttpProtocol.HTTP_1_0.getProtocolName()));
    }

    @Test
    public void getFullUriAndPath() throws Exception
    {
        final String url = String.format("http://localhost:%s%s", listenPort.getNumber(), CONTEXT_PATH);
        Request.Get(url).connectTimeout(1000).execute();
        final MuleMessage message = muleContext.getClient().request("vm://out", 1000);
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_REQUEST_URI), is(CONTEXT_PATH));
        assertThat(message.<String>getInboundProperty(HttpConstants.RequestProperties.HTTP_REQUEST_PATH_PROPERTY), is(CONTEXT_PATH));
    }

    @Test
    public void getAllUriParams() throws Exception
    {
        final String url = String.format("http://localhost:%s/%s/%s/%s", listenPort.getNumber(), FIRST_URI_PARAM, SECOND_URI_PARAM_VALUE, THIRD_URI_PARAM_VALUE);
        Request.Post(url).connectTimeout(1000).execute();
        final MuleMessage message = muleContext.getClient().request("vm://out", 1000);
        ParameterMap uriParams = message.getInboundProperty(HttpConstants.RequestProperties.HTTP_URI_PARAMS);
        assertThat(uriParams, IsNull.notNullValue());
        assertThat(uriParams.size(), is(3));
        assertThat(uriParams.get(FIRST_URI_PARAM_NAME), is(FIRST_URI_PARAM));
        assertThat(uriParams.get(SECOND_URI_PARAM_NAME), is(SECOND_URI_PARAM_VALUE));
        assertThat(uriParams.get(THIRD_URI_PARAM_NAME), is(THIRD_URI_PARAM_VALUE));
    }

    @Test
    public void getUriParamInTheMiddle() throws Exception
    {
        final String url = String.format("http://localhost:%s/some-path/%s/some-other-path", listenPort.getNumber(), FIRST_URI_PARAM);
        Request.Post(url).connectTimeout(1000).execute();
        final MuleMessage message = muleContext.getClient().request("vm://out", 1000);
        ParameterMap uriParams = message.getInboundProperty(HttpConstants.RequestProperties.HTTP_URI_PARAMS);
        assertThat(uriParams, IsNull.notNullValue());
        assertThat(uriParams.size(), is(1));
        assertThat(uriParams.get(FIRST_URI_PARAM_NAME), is(FIRST_URI_PARAM));
    }

    public String buildQueryString(Map<String, Object> queryParams) throws UnsupportedEncodingException
    {
        final StringBuilder queryString = new StringBuilder();
        for (String paramName : queryParams.keySet())
        {
            final Object value = queryParams.get(paramName);
            if (value instanceof Collection)
            {
                for (java.lang.Object eachValue : (Collection)value)
                {
                    queryString.append(paramName + "=" + URLEncoder.encode(eachValue.toString(), Charset.defaultCharset().name()));
                    queryString.append("&");
                }
            }
            else
            {
                queryString.append(paramName + "=" + URLEncoder.encode(value.toString(), Charset.defaultCharset().name()));
                queryString.append("&");
            }
        }
        queryString.deleteCharAt(queryString.length() - 1);
        return queryString.toString();
    }

}
