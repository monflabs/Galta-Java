package org.monflabs.galtajs.external.org_joni.bench;

import org.monflabs.galtajs.external.org_joni.Option;
import org.monflabs.galtajs.external.org_joni.Regex;
import org.monflabs.galtajs.external.org_joni.Syntax;
import org.monflabs.galtajs.external.org_joni.encoding.specific.ASCIIEncoding;

public abstract class AbstractBench {
    protected void bench(String _reg, String _str, int warmup, int times) throws Exception {
        byte[] reg = _reg.getBytes();
        byte[] str = _str.getBytes();

        Regex p = new Regex(reg,0,reg.length,Option.DEFAULT,ASCIIEncoding.INSTANCE,Syntax.DEFAULT);

        System.err.println("::: /" + _reg + "/ =~ \"" + _str + "\", " + warmup + " * " + times + " times");

        for(int j=0;j<warmup;j++) {
            long before = System.currentTimeMillis();
            for(int i = 0; i < times; i++) {
                p.matcher(str, 0, str.length).search(0, str.length, Option.NONE);
            }
            long time = System.currentTimeMillis() - before;
            System.err.println(":  " + time + "ms");
        }
    }

    protected void benchBestOf(String _reg, String _str, int warmup, int times) throws Exception {
        byte[] reg = _reg.getBytes();
        byte[] str = _str.getBytes();

        Regex p = new Regex(reg,0,reg.length,Option.DEFAULT,ASCIIEncoding.INSTANCE,Syntax.DEFAULT);

        System.err.println("::: /" + _reg + "/ =~ \"" + _str + "\", " + warmup + " * " + times + " times");

        long best = Long.MAX_VALUE;

        for(int j=0;j<warmup;j++) {
            long before = System.currentTimeMillis();
            for(int i = 0; i < times; i++) {
                p.matcher(str, 0, str.length).search(0, str.length, Option.NONE);
            }
            long time = System.currentTimeMillis() - before;
            if(time < best) {
                best = time;
            }
            System.err.print(".");
        }
        System.err.println(":  " + best + "ms");
    }
}
