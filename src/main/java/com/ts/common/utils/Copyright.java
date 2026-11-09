/*
 * Description: Prints the application copyright banner at startup
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.utils;
/**
 * Prints the application copyright/license banner to standard output at startup.
 */
public class Copyright {
    // Define copyright information constants
    private static final String COPYRIGHT = "Copyright (c) 2024-2025";
    private static final String AUTHORS = "Rock Rao";
    private static final String VERSION = "v1.0";
    private static final String APP_NAME = "Task Scheduler";
    private static final String BUILD_DATE = "2024-03-27";
    private static final String LICENSE_TEXT =
            "This software may be freely used and modified, provided that this\n" +
                    "version identifier and copyright information are preserved.\n" +
                    "Removal or modification of this notice is prohibited.";

    /**
     * Prints the copyright.
     */
    public static void printCopyright() {
        // Use ASCII art to display copyright information
        System.out.println("" +
                "  _____         _      _____      _              _       _           \n" +
                " |_   _|_ _ ___| | __ / / ___|  _| |__   ___  __| |_   _| | ___ _ __ \n" +
                "   | |/ _` / __| |/ / | \\___ \\ / | '_ \\ / _ \\/ _` | | | | |/ _ \\ '__|\n" +
                "   | | (_| \\__ \\   < < |___) | | | | | |  __/ (_| | |_| | |  __/ |   \n" +
                "   |_|\\__,_|___/_|\\_\\ |____/   |_|_| |_|\\___|\\__,_|\\__,_|_|\\___|_|   \n" +
                "                                                                      \n" +
                " " + APP_NAME + " " + VERSION + " (Build: " + BUILD_DATE + ")\n" +
                " " + COPYRIGHT + "    Developer: " + AUTHORS + "\n" +
                " \n" +
                " LICENSE NOTICE:\n" +
                " " + LICENSE_TEXT.replace("\n", "\n ")
        );
        System.out.println("-------------------------------------------------------------------\n");
    }
}
