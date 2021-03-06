package lsfusion.interop;

import java.io.Serializable;

public class VMOptions implements Serializable {
    private String initHeapSize;
    private String maxHeapSize;
    private String minHeapFreeRatio;
    private String maxHeapFreeRatio;
    private String vmargs;

    public VMOptions(String initHeapSize, String maxHeapSize, String minHeapFreeRatio, String maxHeapFreeRatio, String vmargs) {
        this.initHeapSize = initHeapSize;
        this.maxHeapSize = maxHeapSize;
        this.minHeapFreeRatio = minHeapFreeRatio;
        this.maxHeapFreeRatio = maxHeapFreeRatio;
        this.vmargs = vmargs;
    }

    public String getInitHeapSize() {
        return initHeapSize;
    }

    public void setInitHeapSize(String initHeapSize) {
        this.initHeapSize = initHeapSize;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }

    public void setMaxHeapSize(String maxHeapSize) {
        this.maxHeapSize = maxHeapSize;
    }

    public String getMinHeapFreeRatio() {
        return minHeapFreeRatio;
    }

    public void setMinHeapFreeRatio(String minHeapFreeRatio) {
        this.minHeapFreeRatio = minHeapFreeRatio;
    }

    public String getMaxHeapFreeRatio() {
        return maxHeapFreeRatio;
    }

    public void setMaxHeapFreeRatio(String maxHeapFreeRatio) {
        this.maxHeapFreeRatio = maxHeapFreeRatio;
    }

    public String getVmargs() {
        return vmargs;
    }

    public void setVmargs(String vmargs) {
        this.vmargs = vmargs;
    }
}
