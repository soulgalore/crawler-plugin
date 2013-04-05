package com.soulgalore.jenkins.plugins.crawler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import com.soulgalore.crawler.core.CrawlerResult;
import com.soulgalore.crawler.core.HTMLPageResponse;
import com.soulgalore.crawler.core.assets.AssetResponse;
import com.soulgalore.crawler.core.assets.AssetsVerificationResult;
import com.soulgalore.crawler.util.StatusCode;

public class CrawlerJunitReport {

	private final CrawlerResult result;
	private final AssetsVerificationResult assetsResult;

	public CrawlerJunitReport(CrawlerResult theResult,
			AssetsVerificationResult theAssetsResult) {
		result = theResult;
		assetsResult = theAssetsResult;
	}

	public boolean writeReport(String destination, PrintStream logger) {
		Element root = new Element("testsuites");
		root.addContent(getPageVerifications());
		root.addContent(getAssetsVerifications());
		Document doc = new Document(root);
		XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());

		logger.println(outputter.outputString(doc));
		
		logger.println("Write to:" + destination);
		
		FileWriter out;
		try {
			out = new FileWriter(new File(destination));
			outputter.output(doc, out);
			return true;
		} catch (IOException e) {
			logger.println("Couldn't create JunitXML file:" + destination + " "
					+ e.toString());
			return false;
		}

	}

	private Element getPageVerifications() {

		Element testSuite = new Element("testsuite");
		testSuite.setAttribute("name", "Crawled pages");
		testSuite.setAttribute("tests", ""
				+ (result.getVerifiedURLResponses().size()
				+ result.getNonWorkingUrls().size()));
		testSuite.setAttribute("failures", ""
				+ result.getNonWorkingUrls().size());

		for (HTMLPageResponse resp : result.getVerifiedURLResponses()) {
			Element testCase = new Element("testcase");
			testCase.setAttribute("name", junitFriendlyUrlName(resp
					.getPageUrl().getUrl()));
			testCase.setAttribute("status",
					StatusCode.toFriendlyName(resp.getResponseCode()));
			testSuite.addContent(testCase);
		}

		for (HTMLPageResponse resp : result.getNonWorkingUrls()) {
			Element testCase = new Element("testcase");
			testCase.setAttribute("name", junitFriendlyUrlName(resp
					.getPageUrl().getUrl()));
			testCase.setAttribute("status",
					StatusCode.toFriendlyName(resp.getResponseCode()));
			Element failure = new Element("failure");
			failure.setAttribute("message",
					"The url " + resp.getPageUrl().getUrl() + " got "
							+ StatusCode.toFriendlyName(resp.getResponseCode())
							+ " and is linked from "
							+ resp.getPageUrl().getReferer());
			testSuite.addContent(testCase);
		}

		return testSuite;

	}

	private Element getAssetsVerifications() {

		Element testSuite = new Element("testsuite");
		testSuite.setAttribute("name", "Crawled assets");
		testSuite.setAttribute("tests", ""
				+ (assetsResult.getWorkingAssets().size()
				+ assetsResult.getNonWorkingAssets().size()));
		testSuite.setAttribute("failures", ""
				+ assetsResult.getNonWorkingAssets().size());

		for (AssetResponse resp : assetsResult.getWorkingAssets()) {
			Element testCase = new Element("testcase");
			testCase.setAttribute("name", junitFriendlyUrlName(resp.getUrl()));
			testCase.setAttribute("status",
					StatusCode.toFriendlyName(resp.getResponseCode()));
			testSuite.addContent(testCase);
		}

		for (AssetResponse resp : assetsResult.getNonWorkingAssets()) {
			Element testCase = new Element("testcase");
			testCase.setAttribute("name", junitFriendlyUrlName(resp.getUrl()));
			testCase.setAttribute("status",
					StatusCode.toFriendlyName(resp.getResponseCode()));
			Element failure = new Element("failure");
			failure.setAttribute(
					"message",
					"The asset " + resp.getUrl() + " got "
							+ StatusCode.toFriendlyName(resp.getResponseCode()));
			testSuite.addContent(testCase);
		}

		return testSuite;

	}

	private static String junitFriendlyUrlName(String url) {
		return url.replace("&", "_");
	}

}
