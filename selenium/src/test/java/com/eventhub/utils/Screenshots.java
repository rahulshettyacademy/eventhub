package com.eventhub.utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Screenshots {
    private Screenshots() {}

    public static void save(WebDriver driver, String name) {
        try {
            Path dir = Path.of("target", "screenshots");
            Files.createDirectories(dir);
            File png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(png.toPath(), dir.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not save screenshot: " + e.getMessage());
        }
    }
}
