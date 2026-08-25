import type { Scenario } from "../api/types";

interface ScenarioPickerProps {
  scenarios: Scenario[];
  selectedId: string;
  disabled: boolean;
  onSelect: (scenarioId: string) => void;
}

export function ScenarioPicker({
  scenarios,
  selectedId,
  disabled,
  onSelect,
}: ScenarioPickerProps) {
  return (
    <section className="scenario-picker" aria-labelledby="scenario-picker-title">
      <div className="section-heading compact-heading">
        <div>
          <p className="section-kicker">Case files</p>
          <h2 id="scenario-picker-title">Choose an incident</h2>
        </div>
        <span className="case-count">{scenarios.length} synthetic cases</span>
      </div>

      <div className="scenario-options">
        {scenarios.map((scenario, index) => {
          const selected = scenario.scenario_id === selectedId;
          return (
            <button
              className={`scenario-option${selected ? " selected" : ""}`}
              type="button"
              key={scenario.scenario_id}
              aria-pressed={selected}
              disabled={disabled}
              onClick={() => onSelect(scenario.scenario_id)}
            >
              <span className="scenario-number">0{index + 1}</span>
              <span className="scenario-option-copy">
                <strong>{scenario.title}</strong>
                <span>{scenario.description}</span>
              </span>
              <span className="scenario-radio" aria-hidden="true" />
            </button>
          );
        })}
      </div>
    </section>
  );
}
