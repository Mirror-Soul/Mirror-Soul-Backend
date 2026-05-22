package com.mirrorsoul.mirrorsoul_api.common.webRTC;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SignalingMessage {

    private String type;
    private String roomId;
    private String from;
    private String to;
    private Object data;
}
