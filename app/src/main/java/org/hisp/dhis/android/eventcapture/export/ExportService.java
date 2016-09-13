/*
 * Copyright (c) 2016, University of Oslo
 *
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.hisp.dhis.android.eventcapture.export;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.ref.WeakReference;

public abstract class ExportService<T extends ExportResponse> extends Service {

    final Messenger messenger = new Messenger(new MessageHandler<T>(this));

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("Service", "onBind. Intent: " + intent);
        return messenger.getBinder();
    }

    // static inner class doesn't hold an implicit reference to the outer class
    private static class MessageHandler<T extends ExportResponse> extends Handler {
        // Using a weak reference to not prevent garbage collection
        private final WeakReference<ExportService<T>> exportServiceWeakReference;

        public MessageHandler(ExportService<T> exportServiceInstance) {
            exportServiceWeakReference = new WeakReference<>(exportServiceInstance);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.replyTo != null && exportServiceWeakReference.get() != null) {
                Message message = Message.obtain();
                Bundle bundle = new Bundle();
                T responseObject = exportServiceWeakReference.get().getResponseObject();
                String responseAsString = exportServiceWeakReference.get().marshallToString(responseObject);
                bundle.putString("data", responseAsString);
                message.setData(bundle);
                try {
                    msg.replyTo.send(message);
                } catch (RemoteException e) {
                    Log.e("EXPORT", "Error sending message to client", e);
                }
            }
        }
    }

    @NonNull
    private String marshallToString(T responseObject) {
        ObjectMapper om = new ObjectMapper();
        String responseString;
        try {
            responseString = om.writeValueAsString(responseObject);
        } catch (JsonProcessingException e) {
            try {
                ExportResponse errorResponse = new ExportResponse();
                errorResponse.setError(e);
                responseString = om.writeValueAsString(errorResponse);
                Log.e("EXPORT", "Unable to marshall object to String: " + responseObject.getClass().toString(), e);
            } catch (JsonProcessingException e1) {
                responseString = "Unable to marshall object to String\n" + e1.toString();
                Log.e("EXPORT", "Unable to marshall object to String", e1);
            }
        }
        return responseString;
    }

    public abstract T getResponseObject();
}
