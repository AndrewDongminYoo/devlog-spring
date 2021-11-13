package com.sparta.devlogspring.utils;

import com.sparta.devlogspring.dto.ArticleRequestDto;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArticleCrawler {

    public ChromeDriver getStarted() {
        ChromeOptions options = new ChromeOptions();
//      options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage", "--allow-remote-connections-from-ips=127.0.0.1");
        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        return driver;
    }

    public void finish(ChromeDriver chromeDriver) {
        chromeDriver.quit();
    }

    public String extractHost(String urlString) {
        String result = "";
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            Pattern pattern = Pattern.compile("(?:blog\\.)*[a-z]+\\.[a-z]{2,4}$");
            Matcher match = pattern.matcher(host);
            if (match.find()) {
                result = match.group();
            }
        } catch (MalformedURLException e) {
            result = e.getMessage();
        }
        return result;
    }

    public ArrayList<ArticleRequestDto> crawlerRouter(String url, String name) {
        ArrayList<ArticleRequestDto> result = new ArrayList<>();
        ArrayList<String> targetList;
        if (Objects.equals(extractHost(url), "velog.io")) {
            targetList = velocityCrawl(url, name);
            for (String target: targetList) {
                result.add(velogCrawler(target, name));
            }
        } else if (Objects.equals(extractHost(url), "tistory.com")) {
            targetList = sitemapCrawl(url, name);
            for (String target: targetList) {
                result.add(metaTagsCrawl(target, name));
            }
        } else if (Objects.equals(extractHost(url), "github.io")) {
            targetList = sitemapCrawl(url, name);
            for (String target: targetList) {
                result.add(metaTagsCrawl(target, name));
            }
        }
        return result;
    }

    public ArrayList<String> sitemapCrawl(String url, String name) {
        ChromeDriver driver = getStarted();
        driver.get(url + "sitemap");
        
        String source = driver.getPageSource();
        ArrayList<String> arrays = new ArrayList<>();
        if (Pattern.compile(url + "\\d+").matcher(source).matches()) {
            Matcher matcher = Pattern.compile(url + "\\d+").matcher(source);
            while (matcher.find()) arrays.add(matcher.group());
        } else {
            String pattern = url + "entry/" + "[%\\-\\w\\d]+";
            Matcher matcher = Pattern.compile(pattern).matcher(source);
            while (matcher.find()) arrays.add(matcher.group());
        }
        finish(driver);
        return arrays;
    }

    public ArrayList<String> velocityCrawl(String url, String name) {
        ChromeDriver driver = getStarted();
        driver.get(url);
        
        String scroll = "window.scrollTo(0, document.body.scrollHeight);";
        String height = "return document.body.scrollHeight;";
        Long lastHeight = (Long) ((JavascriptExecutor) driver).executeScript(height);
        while (true) {
            ((JavascriptExecutor) driver).executeScript(scroll);
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Long newHeight = (Long) ((JavascriptExecutor) driver).executeScript(height);
            if (lastHeight.equals(newHeight)) break;
            lastHeight = newHeight;
        }
        ArrayList<String> resultArray = new ArrayList<>();
        List<WebElement> elementList = driver.findElements(
                By.xpath("//*[@id='root']/div[2]/div[3]/div[4]/div[3]/div/div/a"));
        for (WebElement element : elementList) {
            resultArray.add(element.getAttribute("href"));
        }
        finish(driver);
        return resultArray;
    }

    public ArticleRequestDto metaTagsCrawl(String url, String name) {
        ChromeDriver driver = getStarted();
        driver.get(url);
        String title = driver.findElement(By.cssSelector("meta[property='og:title']")).getAttribute("content");
        String author = driver.findElement(By.cssSelector("meta[property='og:article:author']")).getAttribute("content");
        String siteName = driver.findElement(By.cssSelector("meta[property='og:site_name']")).getAttribute("content");
        String registered = driver.findElement(By.cssSelector("meta[property='og:regDate']")).getAttribute("content");
        String image = driver.findElement(By.cssSelector("meta[property='og:image']")).getAttribute("content");
        String description = driver.findElement(By.cssSelector("meta[property='og:description']")).getAttribute("content");
        Document result = makeJSON(name, title, author, siteName, url, description, image, registered);
        finish(driver);
        return new ArticleRequestDto(result);
    }

    public ArticleRequestDto velogCrawler(String url, String name) {
        ChromeDriver driver = getStarted();
        driver.get(url);
        String title = driver.getTitle();
        String image = driver.findElement(By.cssSelector("meta[property='og:image']")).getAttribute("content");
        String description = driver.findElement(By.cssSelector("meta[property='og:description']")).getAttribute("content");
        String author = driver.findElement(By.cssSelector("span.username a")).getText();
        String siteName = driver.findElement(By.cssSelector("a.user-logo")).getText();
        String registered = driver.findElement(By.cssSelector("div.information > span:nth-child(3)")).getText();
        Document result = makeJSON(name, title, author, siteName, url, description, image, registered);
        finish(driver);
        return new ArticleRequestDto(result);
    }

    public Document makeJSON(String name, String title, String author, String siteName, String url, String description, String image, String registered) {
        Document article = new Document();
        article.put("id", ObjectId.get().toString());
        article.put("name", name);
        article.put("title", title);
        article.put("author", author);
        article.put("siteName", siteName);
        article.put("url", url);
        article.put("description", description);
        article.put("image", image);
        article.put("registered", getLocalTime(registered));
        System.out.println(article);
        return article;
    }

    public LocalDateTime getLocalTime(String dateString) {
        Matcher yesterday = Pattern.compile("[약 ]*(\\d{1,2})일 전").matcher(dateString);
        Matcher hoursAgo = Pattern.compile("[약 ]*(\\d{1,2})시간 전").matcher(dateString);
        Matcher minutesAgo = Pattern.compile("[약 ]*(\\d{1,2})분 전").matcher(dateString);
        Matcher secondsAgo = Pattern.compile("[약 ]*(\\d{1,2})초 전").matcher(dateString);
        Matcher regex1 = Pattern.compile("(\\d{4})년 (\\d{1,2})월 (\\d{1,2})일").matcher(dateString);
        Matcher regex2 = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})").matcher(dateString);
        Matcher regex3 = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})\\+09:00").matcher(dateString);
        if (Objects.equals(dateString,"어제")) {
            return LocalDateTime.now().minusDays(1);
        } else if (yesterday.find()) {
            int day = Integer.parseInt(yesterday.group(1));
            return LocalDateTime.now().minusDays(day);
        } else if (hoursAgo.find()) {
            int hour = Integer.parseInt(hoursAgo.group(1));
            return LocalDateTime.now().minusHours(hour);
        } else if (minutesAgo.find()) {
            int minute = Integer.parseInt(minutesAgo.group(1));
            return LocalDateTime.now().minusMinutes(minute);
        } else if (secondsAgo.find()) {
            int seconds = Integer.parseInt(secondsAgo.group(1));
            return LocalDateTime.now().minusSeconds(seconds);
        } else if (regex1.find()) {
            int year = Integer.parseInt(regex1.group(1));
            int month = Integer.parseInt(regex1.group(2));
            int day = Integer.parseInt(regex1.group(3));
            LocalDate date = LocalDate.of(year, month, day);
            LocalTime time = LocalTime.now();
            return LocalDateTime.of(date, time);
        } else if (regex2.find()) {
            return LocalDateTime.parse(dateString,
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } else if (regex3.find()) {
            return LocalDateTime.parse(dateString,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return LocalDateTime.now();
    }
}
