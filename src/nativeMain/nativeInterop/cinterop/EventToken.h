#ifndef EVENTTOKEN_H
#define EVENTTOKEN_H

#ifdef __cplusplus
extern "C" {
#endif

// Defines the token used to register/unregister events in WebView2
typedef struct EventRegistrationToken {
    long long value;
} EventRegistrationToken;

#ifdef __cplusplus
}
#endif

#endif