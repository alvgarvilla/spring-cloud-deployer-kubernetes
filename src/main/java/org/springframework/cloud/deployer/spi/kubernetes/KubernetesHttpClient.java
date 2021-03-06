/*
 * Copyright 2017-2018 the original author or authors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.springframework.cloud.deployer.spi.kubernetes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zafarkhaja.semver.Version;
import io.fabric8.kubernetes.client.BaseClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

/**
 * Wraps the {@link KubernetesClient} http client to send raw REST requests
 * @author David Turanski
 * @author Ilayaperumal Gopinathan
 **/
class KubernetesHttpClient {

	private static final Log logger = LogFactory.getLog(KubernetesHttpClient.class);
	private final OkHttpClient client;
	private final String masterUrl;


	KubernetesHttpClient(KubernetesClient client) {
		this.client = ((BaseClient)client).getHttpClient();
		this.masterUrl = getMasterUrl(client.getMasterUrl().toString());
	}

	private String getMasterUrl(String masterUrl) {
		return !masterUrl.endsWith("/") ? String.format("%s/", masterUrl) : masterUrl;
	}

	public Response post(String resourceEndpoint, String json) {

		String url = this.masterUrl + resourceEndpoint;

		logger.debug("Posting to " + url);

		Request post = new Request.Builder().post(RequestBody.create(MediaType.parse("application/json"), json))
			.url(url).build();

		Response response = execute((this.client.newCall(post)));
		response.close();
		return response;
	}

	public Response get(String resourceEndpoint, String appId) {
		String url = buildUrl(resourceEndpoint, appId);

		logger.debug("Getting " + url);

		Request get = new Request.Builder().get().url(url).build();

		return execute((this.client.newCall(get)));
	}

	public Response delete(String resourceEndpoint, String appId) {
		String url = buildUrl(resourceEndpoint, appId);

		logger.debug("Deleting " + url);

		Request delete = new Request.Builder().delete().url(url).build();

		Response response = execute((this.client.newCall(delete)));
		response.close();

		return response;
	}

	protected String buildUrl(String resourceEndpoint, String appId) {
		String url = this.masterUrl + resourceEndpoint;

		return StringUtils.hasText(appId) ? url + "/" + appId : url;
	}

	/**
	 * Get API version to use for some of the Controller's API route mappings based on the kubernetes
	 * version used in the underlying cluster.
	 * @param client the KubernetesHttpClient to use
	 * @return the appropriate API version to use
	 */
	public static String getApiVersionForK8sVersionFromCluster(KubernetesHttpClient client) {
		try {
			Response response = client.get("version", "");
			Map<String, String> versionResponse = new ObjectMapper().readValue(response.body().string(),
					new TypeReference<HashMap<String, String>>() {
					});
			return getApiVersion(versionResponse.get("gitVersion"));
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Exception retrieving cluster version info. "+ e.getMessage());
		}
	}

	public static String getApiVersion(String k8sVersionFromCluster) {
		Version version110 = Version.valueOf("1.10.0");
		Pattern p = Pattern.compile("\\d+\\.\\d+\\.\\d");
		Matcher m = p.matcher(k8sVersionFromCluster);
		while (m.find()) {
			k8sVersionFromCluster = m.group();
		}
		return Version.valueOf(k8sVersionFromCluster).greaterThanOrEqualTo(version110) ? "v1" : "v1beta1";
	}

	private Response execute(Call call) {
		Response response;
		try {
			response = call.execute();
			logger.debug("Response code: " + response.code());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return response;
	}

}
