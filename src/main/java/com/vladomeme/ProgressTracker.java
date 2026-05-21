package com.vladomeme;

public class ProgressTracker {

    static int counter;
    static int split;
    static int current;

    static void reset(int count) {
        split = count / 100;
        current = 0;
        counter = 0;
    }

    static void set(int progress) {
        counter = progress;
        if (counter < 10) System.out.print("\b\b\b " + counter + "%");
        else if (counter == 100) System.out.println("\b\b\b\b " + counter + "%");
        else if (counter < 100) System.out.print("\b\b\b\b " + counter + "%");
    }

    static void progress() {
        if (++current == split) {
            current = 0;
            counter++;
            if (counter < 10) System.out.print("\b\b\b " + counter + "%");
            else if (counter == 100) System.out.println("\b\b\b\b " + counter + "%");
            else if (counter < 100) System.out.print("\b\b\b\b " + counter + "%");
        }
    }

    static void end() {
        counter = 100;
        System.out.println("\b\b\b\b " + counter + "%");
    }
}
