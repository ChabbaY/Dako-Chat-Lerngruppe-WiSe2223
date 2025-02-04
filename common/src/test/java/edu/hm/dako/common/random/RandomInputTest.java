package edu.hm.dako.common.random;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

class RandomInputTest {

    /**
     * ensuring that every generated name is unique
     */
    @Test
    void randomName() {
        HashMap<Integer, String> names = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String random = RandomInput.randomName();

            assert !names.containsValue(random);
            names.put(random.hashCode(), random);
        }
    }
}