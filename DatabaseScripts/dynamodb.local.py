import boto3
ddb = boto3.client('dynamodb', endpoint_url='http://localhost:8043/')
response = ddb.list_tables()
dynamodb = boto3.resource('dynamodb', region_name='eu-west-1', endpoint_url="http://localhost:8043")
table = dynamodb.Table('requests_data')
table.delete()
print(response)
