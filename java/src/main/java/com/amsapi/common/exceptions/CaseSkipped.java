package com.amsapi.common.exceptions;

/**
 * 用例跳过异常（依赖未满足等情况）。
 * 对应 Python 版 common/exceptions.py 中的 CaseSkipped。
 * 用例层应将其转为测试 skip，而非 fail。
 */
public class CaseSkipped extends RuntimeException {
    public CaseSkipped(String message) {
        super(message);
    }
}
