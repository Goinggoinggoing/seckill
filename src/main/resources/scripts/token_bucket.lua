-- Define a helper function: remove any double quotes from the parameter and convert it to a number
local function toNumber(str)
    if str == nil then
        return nil
    end
    -- Remove double quotes
    str = string.gsub(str, '"', '')
    return tonumber(str)
end

-- Retrieve parameters and convert them to numeric values
local rate = toNumber(ARGV[1])
local capacity = toNumber(ARGV[2])
local now = toNumber(ARGV[3])
local requested = toNumber(ARGV[4])

if now == nil then
    return redis.error_reply("now parameter is nil")
end

-- Read the current token count and last update time from the Redis hash
local data = redis.call('HMGET', KEYS[1], 'tokens', 'timestamp')
local tokens = tonumber(data[1])
local last_time = tonumber(data[2])

-- If not initialized, set to full bucket state
if tokens == nil or last_time == nil then
    tokens = capacity
    last_time = now
end

-- Generate new tokens based on the elapsed time (in milliseconds)
local delta = math.max(0, now - last_time)
local generated = delta * rate / 1000
tokens = math.min(capacity, tokens + generated)

-- Update the time to the current time
last_time = now

if tokens >= requested then
    -- Sufficient tokens available, deduct the requested tokens
    tokens = tokens - requested
    redis.call('HMSET', KEYS[1], 'tokens', tokens, 'timestamp', last_time)
    return 1
else
    -- Insufficient tokens, calculate the waiting time required (in milliseconds)
    local missing = requested - tokens
    local wait_time = math.ceil(missing * 1000 / rate)
    redis.call('HMSET', KEYS[1], 'tokens', tokens, 'timestamp', last_time)
    return -wait_time
end