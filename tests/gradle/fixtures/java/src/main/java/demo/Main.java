package demo;

import sprig.user.$Counter;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.out.println(new $Counter().current());
    }
}
