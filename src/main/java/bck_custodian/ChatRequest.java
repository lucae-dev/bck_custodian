package bck_custodian;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class ChatRequest {
    @NotBlank
    private List<Message> messages;

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public record Message(String role, String content) {}
}
