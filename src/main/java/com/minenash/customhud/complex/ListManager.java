package com.minenash.customhud.complex;

import com.minenash.customhud.data.CHFormatting;

import java.util.List;
import java.util.Stack;
import java.util.function.Supplier;

public class ListManager {

    private static final Stack<Integer> index = new Stack<>();
    private static final Stack<List<?>> values = new Stack<>();
    private static final Stack<CHFormatting> color = new Stack<>();

    //REMOVED: CHFormatting input
    public static void push(List<?> values) {
        ListManager.index.push(0);
        ListManager.values.push(values);
//        ListManager.color.push(formatting);
    }

    //REMOVED: RETURNED COLOR
    public static void pop() {
        ListManager.index.pop();
        ListManager.values.pop();
//        return color.pop();
    }

    public static void advance() {
        ListManager.index.push(ListManager.index.pop()+1);
    }

    public static int getCount() {
        return values.peek().size();
    }

    public static int getIndex() {
        return index.peek();
    }

    public static Object getValue() {
        return values.empty() ? null : values.peek().get(index.peek());
    }
    public static Object getValue(int index) {
        return values.empty() ? null : values.peek().get(index);
    }

    public static final Supplier<?> SUPPLIER = ListManager::getValue;

}
