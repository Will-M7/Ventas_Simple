export function assertDestructiveAllowed(env: NodeJS.ProcessEnv): void {
  if (env.NODE_ENV === 'production' || env.ALLOW_DESTRUCTIVE_DB_RESET !== 'true') throw new Error('Reset rechazado: requiere ALLOW_DESTRUCTIVE_DB_RESET=true y un entorno no productivo');
}
