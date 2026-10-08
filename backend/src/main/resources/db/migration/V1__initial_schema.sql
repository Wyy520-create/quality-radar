CREATE TABLE projects (
  id UUID PRIMARY KEY,
  slug VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE test_runs (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  branch VARCHAR(120) NOT NULL,
  commit_sha VARCHAR(80),
  executed_at TIMESTAMPTZ NOT NULL,
  total INTEGER NOT NULL,
  passed INTEGER NOT NULL,
  failed INTEGER NOT NULL,
  errored INTEGER NOT NULL,
  skipped INTEGER NOT NULL,
  duration_seconds DOUBLE PRECISION NOT NULL,
  report_sha256 VARCHAR(64) NOT NULL
);
CREATE INDEX idx_test_runs_project_time ON test_runs(project_id, executed_at DESC);

CREATE TABLE test_results (
  id UUID PRIMARY KEY,
  test_run_id UUID NOT NULL REFERENCES test_runs(id) ON DELETE CASCADE,
  suite_name VARCHAR(255) NOT NULL,
  class_name VARCHAR(255),
  test_name VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL,
  duration_seconds DOUBLE PRECISION NOT NULL,
  failure_type VARCHAR(255),
  failure_message TEXT,
  failure_fingerprint VARCHAR(64)
);
CREATE INDEX idx_test_results_run ON test_results(test_run_id);
CREATE INDEX idx_test_results_fingerprint ON test_results(failure_fingerprint);

CREATE TABLE risk_assessments (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  score INTEGER NOT NULL,
  level VARCHAR(16) NOT NULL,
  changed_files INTEGER NOT NULL,
  additions INTEGER NOT NULL,
  deletions INTEGER NOT NULL,
  affected_components JSONB NOT NULL,
  factors JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE gate_evaluations (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id),
  test_run_id UUID NOT NULL REFERENCES test_runs(id),
  risk_assessment_id UUID NOT NULL REFERENCES risk_assessments(id),
  status VARCHAR(16) NOT NULL,
  violations JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
