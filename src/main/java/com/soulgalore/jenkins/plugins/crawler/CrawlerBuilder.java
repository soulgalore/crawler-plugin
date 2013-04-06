/******************************************************
 * Crawler for Jenkins
 * 
 *
 * Copyright (C) 2013 by Peter Hedenskog (http://peterhedenskog.com)
 *
 ******************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in 
 * compliance with the License. You may obtain a copy of the License at
 * 
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is 
 * distributed  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   
 * See the License for the specific language governing permissions and limitations under the License.
 *
 *******************************************************
 */
package com.soulgalore.jenkins.plugins.crawler;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.soulgalore.crawler.core.CrawlerConfiguration;
import com.soulgalore.crawler.core.CrawlerResult;
import com.soulgalore.crawler.core.Crawler;
import com.soulgalore.crawler.core.HTMLPageResponse;
import com.soulgalore.crawler.core.assets.AssetResponse;
import com.soulgalore.crawler.core.assets.AssetsVerificationResult;
import com.soulgalore.crawler.core.assets.AssetsVerifier;
import com.soulgalore.crawler.guice.CrawlModule;
import com.soulgalore.crawler.util.StatusCode;
import com.soulgalore.jenkins.plugins.crawler.blocks.EnableAuthBlock;
import com.soulgalore.jenkins.plugins.crawler.blocks.EnableCrawlerInternalsBlock;
import com.soulgalore.jenkins.plugins.crawler.blocks.EnableCrawlerPathBlock;

/**
 * Jenkins plugin that verifies linked internal urls and assets.
 */
public class CrawlerBuilder extends Builder {

	// all different configs
	
	/**
	 * The start url of the crawl.
	 */
	private final String url;
	
	/**
	 * How deep you want to crawl.
	 */
	private final int level;
	
	/**
	 * The login if you are using basic auth.
	 */
	private final String login;
	
	/**
	 * The password if you are using basic auth.
	 */
	private final String password;
	
	/**
	 * If auth is checked or not.
	 */
	private final boolean checkAuth;
	
	/**
	 * If crawler internals is checked or not.
	 */
	private final boolean checkCrawler;
	
	/**
	 * If the crawler path specifics is checked or not.
	 */
	private final boolean checkCrawlerPath;
	
	/**
	 * The number of HTTP threads for the crawl client.
	 */
	private final String httpThreads;
	
	/**
	 * The number of threads in the pool that will parse the responses.
	 */
	private final String threadsPool;
	
	/**
	 * The socket timeout.
	 */
	private final String socketTimeout;
	
	/**
	 * The connection timeout.
	 */
	private final String connectionTimeout;

	/**
	 * Follow only this path in the crawl.
	 */
	private final String followPath;
	
	/**
	 * Do not include pages in this path in the crawl.
	 */
	private final String notFollowPath;
	
	/**
	 * Should assets also be verified?
	 */
	private final boolean verifyAssets;

	@DataBoundConstructor
	public CrawlerBuilder(String url, int level, boolean verifyAssets,
			EnableAuthBlock checkAuth,
			EnableCrawlerInternalsBlock checkCrawler,
			EnableCrawlerPathBlock checkCrawlerPath) {

		this.url = url;
		this.level = level;
		this.verifyAssets = verifyAssets;

		this.login = checkAuth == null ? "" : checkAuth.getLogin();
		this.password = checkAuth == null ? "" : checkAuth.getPassword();
		this.checkAuth = checkAuth == null ? false : true;

		this.httpThreads = checkCrawler == null ? "" : checkCrawler
				.getHttpThreads();
		this.threadsPool = checkCrawler == null ? "" : checkCrawler
				.getThreadsPool();
		this.socketTimeout = checkCrawler == null ? "" : checkCrawler
				.getSocketTimeout();
		this.connectionTimeout = checkCrawler == null ? "" : checkCrawler
				.getConnectionTimeout();
		this.checkCrawler = checkCrawler == null ? false : true;

		this.followPath = checkCrawlerPath == null ? "" : checkCrawlerPath
				.getFollowPath();
		this.notFollowPath = checkCrawlerPath == null ? "" : checkCrawlerPath
				.getNotFollowPath();
		this.checkCrawlerPath = checkCrawlerPath == null ? false : true;

	}

	public boolean isVerifyAssets() {
		return verifyAssets;
	}

	public String getConnectionTimeout() {
		return connectionTimeout;
	}

	public String getFollowPath() {
		return followPath;
	}

	public String getHttpThreads() {
		return httpThreads;
	}

	public int getLevel() {
		return level;
	}

	public String getLogin() {
		return login;
	}

	public String getNotFollowPath() {
		return notFollowPath;
	}

	public String getPassword() {
		return password;
	}

	public String getSocketTimeout() {
		return socketTimeout;
	}

	public String getThreadsPool() {
		return threadsPool;
	}

	public String getUrl() {
		return url;
	}

	public boolean isCheckAuth() {
		return checkAuth;
	}

	public boolean isCheckCrawler() {
		return checkCrawler;
	}

	public boolean isCheckCrawlerPath() {
		return checkCrawlerPath;
	}
	
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) {

	
		PrintStream logger = listener.getLogger();
		
		logger.println("Start crawling:" + url + " for " + level + " level(s) "
				+ (verifyAssets ? " will verify assets" : ""));

		CrawlerConfiguration configuration = CrawlerConfiguration.builder()
				.setMaxLevels(level).setVerifyUrls(true)
				.setOnlyOnPath(followPath).setNotOnPath(notFollowPath)
				.setStartUrl(url).build();

		setupCrawlerInternals();

		setupAuth();

		Injector injector = Guice.createInjector(new CrawlModule());
		Crawler crawler = injector.getInstance(Crawler.class);
		AssetsVerifier verifier = injector.getInstance(AssetsVerifier.class);

		try {

			final CrawlerResult result = crawler.getUrls(configuration);

			boolean isPagesOk = verifyPages(result, logger);
			boolean isAssetsOk = true;

			AssetsVerificationResult assetResults = null;
			
			if (verifyAssets) {
				assetResults = verifyAssets(verifier, result, configuration,
						logger);
				
				if (assetResults.getNonWorkingAssets().size()>0)
					isAssetsOk = false;
			}

			
			CrawlerJunitReport reporter = new CrawlerJunitReport(result,assetResults);
			reporter.writeReport(build.getWorkspace(), logger);
			
			
			return (isPagesOk && isAssetsOk);

		} finally {
			crawler.shutdown();
			verifier.shutdown();
		}

	}

	private boolean verifyPages(CrawlerResult result, PrintStream logger) {

		boolean isFailing = false;

		logger.println("Tested "
				+ (result.getNonWorkingUrls().size() + result
						.getVerifiedURLResponses().size()) + " with "
				+ result.getVerifiedURLResponses().size()
				+ " working urls and " + result.getNonWorkingUrls().size()
				+ " not working.");

		// start with non working urls
		Set<HTMLPageResponse> responses = result.getNonWorkingUrls();
		if (responses.size() > 0) {
			logger.println("Non working urls ...");
			isFailing = true;
		}

		for (HTMLPageResponse response : responses) {
			logger.println(response.getPageUrl().getUrl() + " "
					+ StatusCode.toFriendlyName(response.getResponseCode())
					+ " linked from:" + response.getPageUrl().getReferer());
		}

		logger.println("Working urls ...");
		for (HTMLPageResponse response : result.getVerifiedURLResponses()) {
			logger.println(response.getPageUrl().getUrl());
		}

		if (isFailing)
			return false;
		else
			return true;
	}

	private AssetsVerificationResult verifyAssets(AssetsVerifier verifier, CrawlerResult result,
			CrawlerConfiguration configuration, PrintStream logger) {

		logger.println("Start verifying assets");
		AssetsVerificationResult assetsResult = verifier.verify(
				result.getVerifiedURLResponses(), configuration);
		logger.println("Verified " + assetsResult.getWorkingAssets().size()
				+ " assets that works and "
				+ assetsResult.getNonWorkingAssets().size()
				+ " that don't work");

		if (assetsResult.getNonWorkingAssets().size() > 0) {

			for (AssetResponse resp : assetsResult.getNonWorkingAssets()) {
				logger.println(resp.getUrl() + " "
						+ StatusCode.toFriendlyName(resp.getResponseCode()));
			}
		}

		return assetsResult;
	}

	private void setupCrawlerInternals() {

		if (!"".equals(httpThreads))
			System.setProperty(CrawlerConfiguration.MAX_THREADS_PROPERTY_NAME,
					httpThreads);
		else if (!"".equals(threadsPool))
			System.setProperty("com.soulgalore.crawler.threadsinworkingpool",
					threadsPool);
		else if (!"".equals(socketTimeout))
			System.setProperty(
					CrawlerConfiguration.SOCKET_TIMEOUT_PROPERTY_NAME,
					socketTimeout);
		else if (!"".equals(connectionTimeout))
			System.setProperty(
					CrawlerConfiguration.CONNECTION_TIMEOUT_PROPERTY_NAME,
					connectionTimeout);

	}

	private void setupAuth() {

		if (!"".equals(login) && !"".equals(password)) {
			try {
				URL u = new URL(url);
				String host = u.getHost()
						+ (u.getPort() != -1 ? ":" + u.getPort() : ":80");
				System.setProperty("com.soulgalore.crawler.auth", host + ":"
						+ login + ": " + password);
			} catch (MalformedURLException e) {
				System.err.println(e);
			}

		}

	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {

		public FormValidation doCheckUrl(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set a start url");
			if ((!value.startsWith("http://"))
					&& (!value.startsWith("https://")))
				return FormValidation
						.warning("The url must start with http:// or https:// !");
			return FormValidation.ok();
		}

		public String getDisplayName() {
			return "Crawler";
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}
	}
}
