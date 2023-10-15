package com.jbrowndevelopment.criticalpath;

public final class JavaInteropDemo {
    private JavaInteropDemo() {
    }

    public static void main(String[] args) {
        System.out.println("Java -> Scala interop demo");
        System.out.println();
        System.out.println(PlannerFacade.sampleReport());
    }
}
