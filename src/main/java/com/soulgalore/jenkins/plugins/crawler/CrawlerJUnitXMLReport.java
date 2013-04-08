package com.soulgalore.jenkins.plugins.crawler;

import hudson.FilePath;

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

public class CrawlerJUnitXMLReport {

	public static final String FILENAME = "crawler-junit.xml";

	public CrawlerJUnitXMLReport() {
	}

	public boolean verifyAndWriteReport(CrawlerResult result,
			AssetsVerificationResult assetsResult, FilePath workSpace,
			PrintStream logger) {
		boolean isSuccess = true;
		Element root = new Element("testsuites");
		root.setAttribute("name", "the crawler suites");
		root.addContent(getPageVerifications(result));

		if (result.getNonWorkingUrls().size() > 0)
			isSuccess = false;

		if (assetsResult != null) {
			root.addContent(getAssetsVerifications(assetsResult));
			if (assetsResult.getNonWorkingAssets().size() > 0)
				isSuccess = false;
		}
		Document doc = new Document(root);
		XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());

		logger.println(outputter.outputString(doc));

		try {
			FilePath junitXML = workSpace.child(FILENAME);
			outputter.output(doc, junitXML.write());
			logger.println("Wrote file " + FILENAME + " to workspace");

		} catch (Exception e) {
			logger.println("Couldn't create JunitXML file " + FILENAME
					+ e.toString());
			isSuccess = false;
		}
		return isSuccess;

	}

	private Element getPageVerifications(CrawlerResult result) {

		Element testSuite = new Element("testsuite");
		testSuite.setAttribute("name", "Crawled pages");
		testSuite.setAttribute("tests", ""
				+ (result.getVerifiedURLResponses().size() + result
						.getNonWorkingUrls().size()));
		testSuite.setAttribute("failures", ""
				+ result.getNonWorkingUrls().size());

		long testSuiteTime = 0;
		for (HTMLPageResponse resp : result.getVerifiedURLResponses())
			testSuiteTime += resp.getFetchTime();
		for (HTMLPageResponse resp : result.getNonWorkingUrls())
			testSuiteTime += resp.getFetchTime();
		testSuite.setAttribute("time", "" + (testSuiteTime / 1000.0D));

		for (HTMLPageResponse resp : result.getVerifiedURLResponses()) {
			Element testCase = new Element("testcase");
			testCase.setAttribute("name", junitFriendlyUrlName(resp
					.getPageUrl().getUrl()));
			testCase.setAttribute("status",
					StatusCode.toFriendlyName(resp.getResponseCode()));
			testCase.setAttribute("time", "" + (resp.getFetchTime() / 1000.0D));
			testSuite.addContent(testCase);
		}

		for (HTMLPageResponse resp : result.getNonWorkingUrls()) {
			Element testCase = new Element("testcase");
			testCase.setAttribute("name", junitFriendlyUrlName(resp
					.getPageUrl().getUrl()));
			testCase.setAttribute("status",
					StatusCode.toFriendlyName(resp.getResponseCode()));
			testCase.setAttribute("time", "" + (resp.getFetchTime() / 1000.0D));
			Element failure = new Element("failure");
			failure.setAttribute("message",
					"The url " + resp.getPageUrl().getUrl() + " got "
							+ StatusCode.toFriendlyName(resp.getResponseCode())
							+ " and is linked from "
							+ resp.getPageUrl().getReferer());
			testCase.addContent(failure);
			testSuite.addContent(testCase);
		}

		return testSuite;

	}

	private Element getAssetsVerifications(AssetsVerificationResult assetsResult) {

		Element testSuite = new Element("testsuite");
		testSuite.setAttribute("name", "Crawled assets");
		testSuite.setAttribute("tests", ""
				+ (assetsResult.getWorkingAssets().size() + assetsResult
						.getNonWorkingAssets().size()));
		testSuite.setAttribute("failures", ""
				+ assetsResult.getNonWorkingAssets().size());

		long testSuiteTime = 0;
		for (AssetResponse resp : assetsResult.getWorkingAssets())
			testSuiteTime += resp.getFetchTime();
		for (AssetResponse resp : assetsResult.getNonWorkingAssets())
			testSuiteTime += resp.getFetchTime();
		testSuite.setAttribute("time", "" + (testSuiteTime / 1000.0D));

		for (AssetResponse resp : assetsResult.getWorkingAssets()) {
			Element testCase = new Element("testcase");
			testCase.setAttribute("name", junitFriendlyUrlName(resp.getUrl()));
			testCase.setAttribute("status",
					StatusCode.toFriendlyName(resp.getResponseCode()));
			testCase.setAttribute("time", "" + (resp.getFetchTime() / 1000.0D));
			testSuite.addContent(testCase);
		}

		for (AssetResponse resp : assetsResult.getNonWorkingAssets()) {
			Element testCase = new Element("testcase");
			testCase.setAttribute("name", junitFriendlyUrlName(resp.getUrl()));
			testCase.setAttribute("status",
					StatusCode.toFriendlyName(resp.getResponseCode()));
			testCase.setAttribute("time", "" + (resp.getFetchTime() / 1000.0D));
			Element failure = new Element("failure");
			failure.setAttribute(
					"message",
					"The asset " + resp.getUrl() + " got "
							+ StatusCode.toFriendlyName(resp.getResponseCode()));
			testCase.addContent(failure);
			testSuite.addContent(testCase);
		}

		return testSuite;

	}

	private static String junitFriendlyUrlName(String url) {
		return url.replace("&", "_");
	}

}
