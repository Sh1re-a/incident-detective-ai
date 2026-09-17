import type {
  DemoCustomerChatConversationTurn,
  DemoCustomerChatTurnResponse,
  KnowledgeRagLocale,
} from "./api/generated";

export type CustomerChatTurn = {
  clientId: string;
  question: string;
  locale: KnowledgeRagLocale;
  response: DemoCustomerChatTurnResponse | null;
  errorCode: string | null;
};

export function buildCustomerChatConversation(
  turns: CustomerChatTurn[],
): DemoCustomerChatConversationTurn[] {
  return turns
    .filter((turn): turn is CustomerChatTurn & { response: DemoCustomerChatTurnResponse } =>
      turn.response !== null
      && turn.errorCode === null
      && !turn.response.submitted_message.redacted
      && !["refused", "unavailable", "confirmation_required"].includes(
        turn.response.outcome,
      ),
    )
    .slice(-6)
    .map((turn) => ({
      customer_message: turn.response.submitted_message.text,
      assistant_message: turn.locale === "sv"
        ? turn.response.assistant_message.text_sv
        : turn.response.assistant_message.text_en,
    }));
}
