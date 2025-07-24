package dev.iq.graph.access;

public class Access {
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
    
    public static void main(String[] args) {
        Access app = new Access();
        System.out.println(app.greet("World"));
    }
}