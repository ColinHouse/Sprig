package demo;

import sprig.user.ordinary_project.$Counter;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.out.println(new $Counter().current());
    }
}
