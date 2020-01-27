/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import org.niis.xroad.restapi.exceptions.DeviationAwareException;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Root class for all checked service layer exceptions
 */
public abstract class ServiceException extends DeviationAwareException {

    public ServiceException(String msg, Throwable t, ErrorDeviation errorDeviation) {
        super(msg, t, errorDeviation);
    }

    public ServiceException(String msg, Throwable t, ErrorDeviation errorDeviation,
            Collection<WarningDeviation> warningDeviations) {
        super(msg, t, errorDeviation, warningDeviations);
    }

    public ServiceException(ErrorDeviation errorDeviation, Collection<WarningDeviation> warningDeviations) {
        super(errorDeviation, warningDeviations);
    }

    public ServiceException(Throwable t, ErrorDeviation errorDeviation) {
        super(t, errorDeviation);
    }

    public ServiceException(Throwable t, ErrorDeviation errorDeviation,
            Collection<WarningDeviation> warningDeviations) {
        super(t, errorDeviation, warningDeviations);
    }

    public ServiceException(ErrorDeviation errorDeviation) {
        super(errorDeviation);
    }

    public ServiceException(String msg, ErrorDeviation errorDeviation) {
        super(msg, errorDeviation);
    }

    /**
     * Throws the caught ServiceException as a {@link DeviationAwareRuntimeException}. The cause and
     * {@link ErrorDeviation#metadata ErrorDeviation metadata} will be transferred from the original exception
     * but a new error code must be provided. If the underlying exception does not have error metadata then the
     * localized message of the underlying exception is used as metadata instead. If there is no underlying exception
     * at all, then only the error code will be returned within the ErrorDeviation object.
     * @param newErrorCode the new error code of the exception
     */
    public void throwAsDeviationAwareRuntimeException(String newErrorCode) {
        Throwable t = null;
        List<String> errorMetaData = null;
        if (this.getErrorDeviation() != null
                && this.getErrorDeviation().getMetadata() != null
                && !this.getErrorDeviation().getMetadata().isEmpty()) {
            errorMetaData = this.getErrorDeviation().getMetadata();
        }
        if (this.getCause() != null) {
            t = this.getCause();
            if (errorMetaData == null) {
                errorMetaData = Collections.singletonList(t.getLocalizedMessage());
            }
        }
        throw new DeviationAwareRuntimeException(t, new ErrorDeviation(newErrorCode, errorMetaData));
    }
}
